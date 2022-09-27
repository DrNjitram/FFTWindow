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

import fiji.util.gui.GenericDialogPlus;

import ij.*;
import ij.gui.ImageWindow;
import ij.gui.Roi;
import ij.gui.StackWindow;
import ij.io.Opener;
import ij.plugin.*;

import ij.process.*;
import ij.io.FileSaver;
import ij.WindowManager;
import ij.gui.YesNoCancelDialog;


import net.imagej.ops.OpService;


import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;

import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;


import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;


import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.*;
import javax.swing.*;
import java.awt.event.ActionListener;
import java.util.concurrent.atomic.DoubleAccumulator;
import java.util.logging.Filter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.Math.*;


@Plugin(type = Command.class)
public class FFTWindow implements Command {

    @Parameter
    private LogService logService;

    @Parameter(label = "Window Type", choices = {"Hanning", "Blackman", "Custom"}, description = "Hanning")
    private String windowType;

    @Parameter(label = "Custom Window", required = false)
    private ImagePlus customWindow;

    ImagePlus imp, Result;
    ImageProcessor FilterProcessor;

    public static ImagePlus run(ImagePlus imp, String window) {
        FFTWindow fftWindow = new FFTWindow();
        fftWindow.imp = imp;
        fftWindow.windowType = window;
        fftWindow.run();
        return fftWindow.Result;
    }

    public static ImagePlus run(ImagePlus imp, ImageProcessor filterProcessor) {
        FFTWindow fftWindow = new FFTWindow();
        fftWindow.imp = imp;
        fftWindow.FilterProcessor = filterProcessor;
        fftWindow.windowType = "Custom";
        fftWindow.run();
        return fftWindow.Result;
    }

    @Override
    public void run() {
        if(imp == null)
            imp = IJ.getImage();


        System.out.println("Image: " + imp.getTitle() + ", Window: " + windowType);

        if(FilterProcessor == null) {
            FilterProcessor = getWindow(windowType);
        }

        ImagePlus FFTImp = FFT.forward(imp);

        System.out.println("FilterProcessor test");
        Result = filter(imp, FilterProcessor);
        System.out.println("Apply Filter test");
        //FFTImp.show();
        Result.show();

    }

    public static ImagePlus filter(ImagePlus imp, ImageProcessor filter) {
        filter = filter.resize(imp.getWidth(), imp.getHeight());
        final double max = filter.maxValue();

        ImageProcessor imageProcessor = (ImageProcessor) imp.getProcessor().clone();

        for (int i=0; i<imageProcessor.getPixelCount(); i++) {
            if(i < 255)
                System.out.println((int)(imageProcessor.get(i)*(filter.get(i)/max)) + " = " + imageProcessor.get(i) + "*(" + filter.get(i) + "/" + max + ")");
            imageProcessor.set(i, (int) (imageProcessor.get(i)*(filter.get(i)/max)));
        }

        return new ImagePlus("filtered " + imp.getTitle(), imageProcessor);
    }

    public ImageProcessor getWindow(String windowType) {
        return null;
    }

    //Only used when debugging from an IDE
    public static void main(String[] args) {
        net.imagej.ImageJ ij = new net.imagej.ImageJ();
        ij.ui().showUI();

        ImagePlus imp = IJ.openImage("H:\\PhD\\FFTWindow\\boats.tif");
        imp.show();

        ImagePlus filter =  IJ.openImage("H:\\PhD\\FFTWindow\\gradient.tif");


        filter.show();
        ImagePlus Result = FFTWindow.run(imp, filter.getProcessor());
        Result.show();

    }
}

