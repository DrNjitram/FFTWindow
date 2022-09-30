package com.wurgobes.fftw;

import ij.gui.GenericDialog;


import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@Plugin(type = Command.class, menuPath = "Plugins>FFTWindow>About FFTWindow...")
public class About implements Command {

    @Parameter
    private LogService log;

    private static String GetInputStream(InputStream is) {
        StringBuilder result = new StringBuilder();
        try (InputStreamReader streamReader =
                     new InputStreamReader(is, StandardCharsets.UTF_8);
             BufferedReader reader = new BufferedReader(streamReader)) {

            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        return result.toString();
    }

    @Override
    public void run() {
        log.info("FFT Window About");
        String content = "Developed by Martijn Gobes at the Holhbein Lab.\nMore information can be found at https://github.com/DrNjitram/FFTWindow/\n" +
                "Current Version: 1.1";

        GenericDialog gd = new GenericDialog("About");
        gd.addMessage(content);

        InputStream is = FFTWindow.class.getResourceAsStream("/README.txt"); // OK
        String help = GetInputStream(is);
        gd.addHelp(help);

        gd.showDialog();
    }


}
