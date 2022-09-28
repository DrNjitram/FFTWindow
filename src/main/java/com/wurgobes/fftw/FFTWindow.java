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



import io.scif.services.DatasetIOService;
import net.imagej.Dataset;
import net.imagej.DatasetService;
import net.imagej.axis.Axes;
import net.imagej.axis.AxisType;
import net.imagej.ops.OpService;


import net.imglib2.*;
import net.imglib2.algorithm.stats.ComputeMinMax;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.Priority;
import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.io.*;
import java.util.function.DoubleBinaryOperator;


@Plugin(type = Command.class, priority = Priority.HIGH, menuPath = "Plugins>FFTWindow>Apply Window")
public class FFTWindow  <T extends RealType<T> & NativeType<T>> implements Command {

    @Parameter
    private LogService log;

    @Parameter
    private OpService opService;

    @Parameter
    private DatasetService datasetService;

    @Parameter
    private DatasetIOService datasetIOService;

    @Parameter(visibility = ItemVisibility.MESSAGE)
    private final String header = "FFTWindow";

    @Parameter(label = "Window Type", choices = {"Bartlett ", "Hanning", "Blackman", "Custom"}, description = "Hanning")
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

    public Dataset run(Dataset dataset, DoubleBinaryOperator equation) {
        return this.run(dataset, equation, new long[]{512, 512});
    }

    public Dataset run(Dataset dataset, DoubleBinaryOperator equation, long[] dims) {
        this.dataset = dataset;
        this.CustomFilter = applyEquation(equation, dims);
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
            } catch (IOException e) {
                e.printStackTrace();
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
        //ImageJFunctions.show(RAI_resized_filter);

        final RandomAccess<? extends RealType> RA_input = input.getImgPlus().randomAccess();
        final RandomAccess<? extends RealType> RA_filter = RAI_resized_filter.randomAccess();
        final Cursor<? extends RealType> cursor = result.getImgPlus().localizingCursor();

        final long[] pos1 = new long[input.numDimensions()];
        final long[] pos2 = new long[resized_filter.numDimensions()];

        final double[] minmax = getMinMax(filter);

        while (cursor.hasNext()) {
            cursor.fwd();
            cursor.localize(pos1);
            cursor.localize(pos2);
            RA_input.setPosition(pos1);
            RA_filter.setPosition(pos2);
            final double filtered_value = RA_input.get().getRealDouble() * ((RA_filter.get().getRealDouble()-minmax[0]) / minmax[1]);
            cursor.get().setReal(filtered_value);
        }

        return result;
    }

    public Dataset getWindow(String windowType) throws IllegalAccessException, IOException {
        DoubleBinaryOperator equation;
        final long[] dims = new long[]{512, 512}; //filter dimensions
        final double[] dims_c = new double[]{dims[0]/2f, dims[1]/2f}; //center of the filter
        final double r_max = Math.min(dims_c[0], dims_c[1]); //maximum distance in pixels to the center
        switch(windowType) {
            case "Bartlett":
                equation = (x, y) -> Bartlett(x, y, dims_c[0], dims_c[1], r_max);
                break;
            case "Blackman":
                equation = (x, y) -> Blackman(x, y, dims_c[0], dims_c[1], r_max);
                break;
            case "Hanning":
                equation = (x, y) -> Hanning(x, y, dims_c[0], dims_c[1], r_max);
                break;
            default:
                throw new IllegalAccessException("Invalid window type " + windowType);
        }

        return applyEquation(equation, dims);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public Dataset applyEquation(DoubleBinaryOperator equation, long[] dims){
        Dataset filterDataset = datasetService.create(new FloatType(), dims, "Filter", new AxisType[]{Axes.X, Axes.Y});
        final IterableInterval<? extends RealType> II_filter = Views.iterable(filterDataset);
        opService.image().equation(II_filter, equation);
        return filterDataset;
    }

    private static double Bartlett(final double x, final double y, final double x_c, final double y_c, final double r_max) {
        double r = Math.sqrt( Math.pow(x - x_c, 2)  + Math.pow(y - y_c, 2)); //distance in pixels to center
        return r > r_max ? 0f : 1 - r/r_max;
    }

    private static double Hanning(final double x, final double y, final double x_c, final double y_c, final double r_max) {
        double r = Math.sqrt( Math.pow(x - x_c, 2)  + Math.pow(y - y_c, 2)); //distance in pixels to center
        return r > r_max ? 0f : 0.5 - 0.5 * Math.cos(Math.PI * (1 - r/r_max));
    }

    private static double Blackman(final double x, final double y, final double x_c, final double y_c, final double r_max) {
        double r = Math.sqrt( Math.pow(x - x_c, 2)  + Math.pow(y - y_c, 2)); //distance in pixels to center
        return r > r_max ? 0f : 0.42 - 0.5 * Math.cos(Math.PI * (1 - r/r_max)) + 0.08 * Math.cos(2* Math.PI * (1 - r/r_max));
    }

    @SuppressWarnings("unchecked")
    private double[] getMinMax(Dataset input) {
        T min = (T) input.firstElement().createVariable();
        T max = (T) input.firstElement().createVariable();

        ComputeMinMax.computeMinMax((RandomAccessibleInterval<T>)input.getImgPlus(), min, max);

        return new double[]{min.getRealDouble(), max.getRealDouble()};
    }

    //Only used when debugging from an IDE
    public static void main(String[] args)  throws IOException  {
        net.imagej.ImageJ ij = new net.imagej.ImageJ();
        ij.ui().showUI();

        Dataset input = (Dataset) ij.io().open("C:\\Users\\gobes001\\LocalSoftware\\FFTWindow\\test.tif");
        //Dataset filter = (Dataset) ij.io().open("C:\\Users\\gobes001\\LocalSoftware\\FFTWindow\\gradient.tif");

        ij.ui().show(input);
        //ij.ui().show(filter);

        ij.command().run(FFTWindow.class, true);

    }
}

