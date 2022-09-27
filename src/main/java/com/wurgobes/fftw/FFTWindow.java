package com.wurgobes.fftw;
/* FFT Window
(c) 2022 Martijn Gobes, Wageningen University.

This software is released under the GPL v3. You may copy, distribute and modify
the software as long as you track changes/dates in source files. Any
modifications to or software including (via compiler) GPL-licensed code
must also be made available under the GPL along with build & install instructions.
https://www.gnu.org/licenses/gpl-3.0.en.html

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
 */


import edu.mines.jtk.sgl.Axis;
import ij.ImagePlus;
import io.scif.formats.ImageIOFormat;
import io.scif.services.DatasetIOService;
import net.imagej.Dataset;
import net.imagej.DatasetService;
import net.imagej.axis.Axes;
import net.imagej.axis.AxisType;
import net.imagej.ops.OpService;


import net.imagej.ops.image.equation.DefaultEquation;
import net.imagej.ops.image.normalize.NormalizeIIFunction;
import net.imglib2.*;


import net.imglib2.img.ImagePlusAdapter;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.Priority;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.display.DisplayService;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.io.*;
import java.util.Random;
import java.util.function.BiFunction;
import java.util.function.DoubleBinaryOperator;


@Plugin(type = Command.class, priority = Priority.HIGH, menuPath = "Plugins>FFTWindow>Apply Window")
public class FFTWindow  <T extends RealType<T> & NativeType<T>> implements Command {

    @Parameter
    private LogService log;

    @Parameter
    private OpService opService;

    @Parameter
    private DatasetService datasetService;

    @Parameter(visibility = ItemVisibility.MESSAGE)
    private final String header = "FFTWindow";

    @Parameter(label = "Window Type", choices = {"Hanning", "Blackman", "Custom"}, description = "Hanning")
    private String windowType;

    @Parameter(label = "Image to filter")
    private Dataset dataset;

    @Parameter(label = "Custom Window")
    private Dataset CustomFilter;

    @Parameter(type = ItemIO.OUTPUT)
    private Dataset Result;


    public Dataset run(Dataset dataset, String window) {
        this.dataset = dataset;
        this.CustomFilter = null;
        this.windowType = window;

        run();

        return Result;
    }

    public Dataset run(Dataset dataset, Dataset filter) {
        this.dataset = dataset;
        this.CustomFilter = filter;
        this.windowType = "Custom";

        run();

        return Result;
    }

    @Override

    public void run() {
        long start_time = System.currentTimeMillis();
        log.info("Running filter on " + dataset.getName() + " with " + windowType + " filter");


        if(!windowType.equals("Custom")) {
            try {
                CustomFilter = getWindow(windowType);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }

        Result = filter(dataset, CustomFilter);
        log.info("Finished applying filter in " + (System.currentTimeMillis() - start_time) + "ms");
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private Dataset filter(final Dataset input, final Dataset filter) {
        final Dataset result = input.copy();
        result.setName("Filter of " + input.getName());
        final Dataset resized_filter = filter.copy();

        final InterpolatorFactory<T, RandomAccessible<T>> interpolator = new NLinearInterpolatorFactory<>();
        final double[] scalars = new double[]{ (double) input.dimension(0)/filter.dimension(0), (double) input.dimension(1)/filter.dimension(1)};
        final RandomAccessibleInterval<T> RAI_resized_filter = opService.transform().scaleView((Img<T>) resized_filter.getImgPlus(), scalars, interpolator);

        final RandomAccess<? extends RealType> RA_input = input.getImgPlus().randomAccess();
        final RandomAccess<? extends RealType> RA_filter = RAI_resized_filter.randomAccess();
        final Cursor<? extends RealType> cursor = result.getImgPlus().localizingCursor();

        final long[] pos1 = new long[input.numDimensions()];
        final long[] pos2 = new long[resized_filter.numDimensions()];

        final double max = Math.max(input.realMax(0), input.realMax(1));

        while (cursor.hasNext()) {
            cursor.fwd();
            cursor.localize(pos1);
            cursor.localize(pos2);
            RA_input.setPosition(pos1);
            RA_filter.setPosition(pos2);
            final double sum = RA_input.get().getRealDouble() * (RA_filter.get().getRealDouble() / max);
            cursor.get().setReal(sum);
        }

        return result;
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Dataset getWindow(String windowType) throws IllegalAccessException {
        Dataset filterDataset = datasetService.create(new FloatType(), new long[]{64, 16}, "Filter", new AxisType[]{Axes.X, Axes.Y});
        final IterableInterval<? extends RealType> II_filter = Views.iterable(filterDataset);


        log.info("Image Created");
        DoubleBinaryOperator equation;

        switch(windowType) {
            case "Blackman":
                equation = (x, y) -> x + y*2;
                break;
            case "Hanning":
                equation = (x, y)  -> x * y;
                break;
            default:
                throw new IllegalAccessException("Invalid window type " + windowType);
        }

        opService.image().equation(II_filter, equation);
        log.info("Equation Applied");

        log.info("Done!");
        return filterDataset;

    }

    //Only used when debugging from an IDE
    public static void main(String[] args)  throws IOException  {
        net.imagej.ImageJ ij = new net.imagej.ImageJ();
        ij.ui().showUI();

        Dataset input = (Dataset) ij.io().open("H:\\PhD\\FFTWindow\\boats.tif");
        Dataset filter = (Dataset) ij.io().open("H:\\PhD\\FFTWindow\\gradient.tif");

        ij.ui().show(input);
        ij.ui().show(filter);

        ij.command().run(FFTWindow.class, true);

    }
}

