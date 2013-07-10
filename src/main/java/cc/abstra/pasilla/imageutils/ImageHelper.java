package cc.abstra.pasilla.imageutils;

import com.mortennobel.imagescaling.AdvancedResizeOp;
import com.mortennobel.imagescaling.DimensionConstrain;
import com.mortennobel.imagescaling.ResampleOp;

import javax.media.jai.PlanarImage;
import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.awt.image.ColorModel;
import java.awt.image.RenderedImage;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author nando
 */
public class ImageHelper {

    static BufferedImage resizeImageToDINA4WithDPI(BufferedImage origImage, int origDpiX, int origDpiY) {

        float targetSizeX = (float)Consts.millisToPixels(Consts.A4_W_MM, origDpiX)/origImage.getWidth();
        float targetSizeY = (float)Consts.millisToPixels(Consts.A4_H_MM, origDpiY)/origImage.getHeight();
        float scalingFactor = (float)getScalingFactor(origImage);

        Logger.getLogger(ImageHelper.class.getName()).log(Level.INFO,
                "Resizing BufferedImage (px) --- "+
                        "Original dimensions: ("+Integer.toString(origImage.getWidth())+
                        ","+Integer.toString(origImage.getHeight())+")"+
                        " - Fraction: ("+Float.toString(targetSizeX)+","+Float.toString(targetSizeY)+")"+
                        " - Scaling factor: "+Float.toString(scalingFactor));

        ResampleOp resampleOp = new ResampleOp(DimensionConstrain.createRelativeDimension(
                scalingFactor*targetSizeX,scalingFactor*targetSizeY));
        resampleOp.setUnsharpenMask(AdvancedResizeOp.UnsharpenMask.Normal);
        return resampleOp.filter(origImage, null);
    }

    private static double getScalingFactor(BufferedImage img){
        // scaling factor to match img's DPI
        double scale = 1.0;
        double scaleX = Consts.A4_W_INCHES * Consts.INCH_TO_POINT / img.getWidth();
        double scaleY = Consts.A4_H_INCHES * Consts.INCH_TO_POINT / img.getHeight();

        if (0.0 < scaleX && scaleX <= 1.0) {
            scale = scaleX;
        } else {
            if (0.0 < scaleY && scaleY <= 1.0) {
                scale = scaleY;
            }
        }
        return scale;
    }


    public static BufferedImage sanitizeImage(RenderedImage origRi, boolean applyAlpha) {
        BufferedImage image;
        if (origRi instanceof BufferedImage) {
            image = (BufferedImage) origRi;
        } else {
            ColorModel cm = PlanarImage.createColorModel(origRi.getSampleModel());
            image = PlanarImage.wrapRenderedImage(origRi).getAsBufferedImage(null, cm);
        }
        if(applyAlpha){  //HACK until TiffDoc.toPdf readers are used in .toPng
            int origColorSpace = image.getColorModel().getColorSpace().getType();
            if(origColorSpace != ColorSpace.TYPE_GRAY && origColorSpace != ColorSpace.TYPE_RGB) {
                image = convertToRGB(image);
            }
        }
        return image;
    }

    private static BufferedImage convertToRGB(BufferedImage src){
        BufferedImage dst = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
        ColorConvertOp op = new ColorConvertOp(null);
        dst =  op.filter(src, dst);
        // CAVEAT:
        // ImageHelper.sanitizeImage(img) might leak brightness from any non-sRGB
        // color space (such as CMYK) to the sRGB target, "sanitized"  space.
        // Because of that artifact, ImageHelper.gammaCorrection() is available.
        ColorSpace origCs = src.getColorModel().getColorSpace();
        if (!origCs.isCS_sRGB()){
          if(ColorSpace.TYPE_CMYK == origCs.getType()){
              Logger.getLogger(ImageHelper.class.getName()).log(Level.INFO,
                      "Applying CMYK to sRGB gamma correction: "+Consts.CMYK_RGB_GAMMA_CORRECTION);
              dst = gammaCorrection(dst,Consts.CMYK_RGB_GAMMA_CORRECTION);
          }
        }
        return dst;
    }

    /**
     * Gamma correction algorithm
     *
     * Author: Bostjan Cigan (http://zerocool.is-a-geek.net)
     *
     *
     * Note: another impl (platform-dependent) can be found at the end of this thread:
     *    https://forums.oracle.com/forums/thread.jspa?messageID=5387100
     *  but it didn't work in Linux.
     */
    private static BufferedImage gammaCorrection(BufferedImage original, double gamma) {

        int alpha, red, green, blue;
        int newPixel;

        double gamma_new = 1 / gamma;
        int[] gamma_LUT = gamma_LUT(gamma_new);

        BufferedImage gamma_cor = new BufferedImage(original.getWidth(), original.getHeight(), original.getType());

        for(int i=0; i<original.getWidth(); i++) {
            for(int j=0; j<original.getHeight(); j++) {

                // Get pixels by R, G, B
                alpha = new Color(original.getRGB(i, j)).getAlpha();
                red = new Color(original.getRGB(i, j)).getRed();
                green = new Color(original.getRGB(i, j)).getGreen();
                blue = new Color(original.getRGB(i, j)).getBlue();

                red = gamma_LUT[red];
                green = gamma_LUT[green];
                blue = gamma_LUT[blue];

                // Return back to original format
                newPixel = colorToRGB(alpha, red, green, blue);

                // Write pixels into image
                gamma_cor.setRGB(i, j, newPixel);

            }

        }

        return gamma_cor;

    }

    // Create the gamma correction lookup table
    private static int[] gamma_LUT(double gamma_new) {
        int[] gamma_LUT = new int[256];

        for(int i=0; i<gamma_LUT.length; i++) {
            gamma_LUT[i] = (int) (255 * (Math.pow((double) i / (double) 255, gamma_new)));
        }

        return gamma_LUT;
    }

    // Convert R, G, B, Alpha to standard 8 bit
    private static int colorToRGB(int alpha, int red, int green, int blue) {

        int newPixel = 0;
        newPixel += alpha;
        newPixel = newPixel << 8;
        newPixel += red; newPixel = newPixel << 8;
        newPixel += green; newPixel = newPixel << 8;
        newPixel += blue;

        return newPixel;

    }
}