/*
 * CustomOutputStream.java
 * 
 * Authors: TEAM 1
 * 
 * This file was created specifically for the course SYSC 3303.
 * 
 * 
 * !!! THIS CODE IS NOT CURRENTLY IN USE !!!
 */

package tftp;

/****************************Code Has been Changed By Brian****************************************/
import java.io.IOException;
import java.io.OutputStream;
 
import javax.swing.JTextArea;
 
/**
 * This class extends from OutputStream to redirect output to a JTextArrea
 * 
 *
 */
public class CustomOutputStream extends OutputStream {
    private JTextArea textArea;
     
    public CustomOutputStream(JTextArea textArea) {
        this.textArea = textArea;
    }
     
    @Override
    public void write(int b) throws IOException {
        // redirects data to the text area
        textArea.append(String.valueOf((char)b));
        // scrolls the text area to the end of data
        textArea.setCaretPosition(textArea.getDocument().getLength());
    }
}

/****************************End of Change****************************************/