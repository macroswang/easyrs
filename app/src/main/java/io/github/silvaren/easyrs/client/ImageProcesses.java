package io.github.silvaren.easyrs.client;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.v8.renderscript.RenderScript;

import java.util.HashMap;
import java.util.Map;

import io.github.silvaren.easyrs.tools.Blend;
import io.github.silvaren.easyrs.tools.Blur;
import io.github.silvaren.easyrs.tools.ColorMatrix;
import io.github.silvaren.easyrs.tools.Convolve;
import io.github.silvaren.easyrs.tools.params.ConvolveParams;
import io.github.silvaren.easyrs.tools.Histogram;
import io.github.silvaren.easyrs.tools.Lut;
import io.github.silvaren.easyrs.tools.Lut3D;
import io.github.silvaren.easyrs.tools.params.Lut3DParams;
import io.github.silvaren.easyrs.tools.params.LutParams;
import io.github.silvaren.easyrs.tools.Nv21Image;
import io.github.silvaren.easyrs.tools.Resize;
import io.github.silvaren.easyrs.tools.base.Utils;
import io.github.silvaren.easyrs.tools.params.SampleParams;

class ImageProcesses {

    enum ImageFormat {
        BITMAP(0),
        NV21(1);

        private final int id;

        ImageFormat(int id) {
            this.id = id;
        }

        public static ImageFormat valueOf(int progress) {
            ImageFormat[] values = values();
            for (ImageFormat format : values) {
                if (format.id == progress)
                    return format;
            }
            return BITMAP;
        }
    }

    public interface ImageProcess {
        Bitmap processImage(RenderScript rs, Bitmap bitmap, ImageFormat imageFormat);
    }

    static Map<String, Integer> flavorMap(Context context) {
        HashMap<String, Integer> flavorMap = new HashMap<>();
        flavorMap.put(context.getString(R.string.colormatrix), R.array.colormatrix_array);
        flavorMap.put(context.getString(R.string.convolve), R.array.convolve_array);
        flavorMap.put(context.getString(R.string.histogram), R.array.histogram_array);
        return flavorMap;
    }

    static Map<String, ImageProcess> processMap(Context context) {
        HashMap<String, ImageProcess> processMap = new HashMap<>();
        processMap.put(context.getString(R.string.original), originalProcess);
        processMap.put(context.getString(R.string.blend), blendProcess(context));
        processMap.put(context.getString(R.string.blur), blurProcess);
        processMap.put(context.getString(R.string.grayscale), colorMatrixGraycaleProcess);
        processMap.put(context.getString(R.string.rgbtoyuv), colorMatrixRgbtoYuvProcess);
        processMap.put(context.getString(R.string.sobel3x3), convolveSobel3x3Process);
        processMap.put(context.getString(R.string.sobel5x5), convolveSobel5x5Process);
        processMap.put(context.getString(R.string.rgba_histogram), rgbaHistogramProcess);
        processMap.put(context.getString(R.string.lum_histogram), lumHistogramProcess);
        processMap.put(context.getString(R.string.lut), lutProcess);
        processMap.put(context.getString(R.string.lut3d), lut3dProcess);
        processMap.put(context.getString(R.string.resize), resizeProcess);
        return processMap;
    }

    private static ImageProcess originalProcess = new ImageProcess() {
        @Override
        public Bitmap processImage(RenderScript rs, Bitmap bitmap, ImageFormat imageFormat) {
            if (imageFormat == ImageFormat.BITMAP)
                return bitmap;
            else {
                Nv21Image nv21Image = Nv21Image.bitmapToNV21(rs, bitmap);
                return Nv21Image.nv21ToBitmap(rs, nv21Image.nv21ByteArray,
                        nv21Image.width, nv21Image.height);
            }
        }
    };

    private static ImageProcess blendProcess(final Context context) {
        return new ImageProcess() {
            @Override
            public Bitmap processImage(RenderScript rs, Bitmap bitmap, ImageFormat imageFormat) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = 4;
                options.inDither = false;
                options.inPurgeable = true;
                Bitmap sampleEdgeBitmap = BitmapFactory.decodeResource(context.getResources(),
                        R.drawable.sample_edge, options);
                if (imageFormat == ImageFormat.BITMAP) {
                    Blend.add(rs, bitmap, sampleEdgeBitmap);
                    return sampleEdgeBitmap;
                } else {
                    Nv21Image nv21Image = Nv21Image.bitmapToNV21(rs, bitmap);
                    Nv21Image dstNv21Image = Nv21Image.bitmapToNV21(rs, sampleEdgeBitmap);
                    Blend.add(rs, nv21Image.nv21ByteArray, nv21Image.width, nv21Image.height,
                            dstNv21Image.nv21ByteArray);
                    return Nv21Image.nv21ToBitmap(rs, dstNv21Image.nv21ByteArray,
                            dstNv21Image.width, nv21Image.height);
                }
            }
        };
    }

    private static ImageProcess blurProcess = new ImageProcess() {
        @Override
        public Bitmap processImage(RenderScript rs, Bitmap bitmap, ImageFormat imageFormat) {
            if (imageFormat == ImageFormat.BITMAP)
                return Blur.blur(rs, bitmap, 25.f);
            else {
                Nv21Image nv21Image = Nv21Image.bitmapToNV21(rs, bitmap);
                byte[] output = Blur.blur(rs, nv21Image.nv21ByteArray, nv21Image.width,
                        nv21Image.height, 25.f);
                return Nv21Image.nv21ToBitmap(rs, output, nv21Image.width, nv21Image.height);
            }
        }
    };

    private static ImageProcess colorMatrixRgbtoYuvProcess = new ImageProcess() {
        @Override
        public Bitmap processImage(RenderScript rs, Bitmap bitmap, ImageFormat imageFormat) {
            return ColorMatrix.rgbToYuv(rs, bitmap);
        }
    };

    private static ImageProcess colorMatrixGraycaleProcess = new ImageProcess() {
        @Override
        public Bitmap processImage(RenderScript rs, Bitmap bitmap, ImageFormat imageFormat) {
            if (imageFormat == ImageFormat.BITMAP)
                return ColorMatrix.convertToGrayScale(rs, bitmap);
            else {
                Nv21Image nv21Image = Nv21Image.bitmapToNV21(rs, bitmap);
                byte[] output = ColorMatrix.convertToGrayScale(rs, nv21Image.nv21ByteArray,
                        nv21Image.width, nv21Image.height);
                return Nv21Image.nv21ToBitmap(rs, output, nv21Image.width, nv21Image.height);
            }
        }
    };

    private static ImageProcess convolveSobel3x3Process = new ImageProcess() {
        @Override
        public Bitmap processImage(RenderScript rs, Bitmap bitmap, ImageFormat imageFormat) {
            if (imageFormat == ImageFormat.BITMAP)
                return Convolve.convolve3x3(rs, bitmap, SampleParams.Convolve.Kernels3x3.SOBEL_X);
            else {
                Nv21Image nv21Image = Nv21Image.bitmapToNV21(rs, bitmap);
                byte[] output = Convolve.convolve3x3(rs, nv21Image.nv21ByteArray,
                        nv21Image.width, nv21Image.height, SampleParams.Convolve.Kernels3x3.SOBEL_X);
                return Nv21Image.nv21ToBitmap(rs, output, nv21Image.width, nv21Image.height);
            }
        }
    };


    private static ImageProcess convolveSobel5x5Process = new ImageProcess() {
        @Override
        public Bitmap processImage(RenderScript rs, Bitmap bitmap, ImageFormat imageFormat) {
            if (imageFormat == ImageFormat.BITMAP)
                return Convolve.convolve5x5(rs, bitmap, SampleParams.Convolve.Kernels5x5.SOBEL_X);
            else {
                Nv21Image nv21Image = Nv21Image.bitmapToNV21(rs, bitmap);
                byte[] output = Convolve.convolve5x5(rs, nv21Image.nv21ByteArray,
                        nv21Image.width, nv21Image.height, SampleParams.Convolve.Kernels5x5.SOBEL_X);
                return Nv21Image.nv21ToBitmap(rs, output, nv21Image.width, nv21Image.height);
            }
        }
    };

    private static ImageProcess rgbaHistogramProcess = new ImageProcess() {
        @Override
        public Bitmap processImage(RenderScript rs, Bitmap bitmap, ImageFormat imageFormat) {
            int[] histograms;
            if (imageFormat == ImageFormat.BITMAP)
                histograms = Histogram.rgbaHistograms(rs, bitmap);
            else {
                Nv21Image nv21Image = Nv21Image.bitmapToNV21(rs, bitmap);
                histograms = Histogram.rgbaHistograms(rs, nv21Image.nv21ByteArray,
                        nv21Image.width, nv21Image.height);
            }
            return Utils.drawHistograms(histograms, 4);
        }
    };

    private static ImageProcess lumHistogramProcess = new ImageProcess() {
        @Override
        public Bitmap processImage(RenderScript rs, Bitmap bitmap, ImageFormat imageFormat) {
            int[] histograms;
            if (imageFormat == ImageFormat.BITMAP)
                histograms = Histogram.luminanceHistogram(rs, bitmap);
            else {
                Nv21Image nv21Image = Nv21Image.bitmapToNV21(rs, bitmap);
                histograms = Histogram.luminanceHistogram(rs, nv21Image.nv21ByteArray,
                        nv21Image.width, nv21Image.height);
            }
            return Utils.drawHistograms(histograms, 1);
        }
    };

    private static ImageProcess lutProcess = new ImageProcess() {
        @Override
        public Bitmap processImage(RenderScript rs, Bitmap bitmap, ImageFormat imageFormat) {
            if (imageFormat == ImageFormat.BITMAP)
                return Lut.applyLut(rs, bitmap, SampleParams.Lut.negative());
            else {
                Nv21Image nv21Image = Nv21Image.bitmapToNV21(rs, bitmap);
                byte[] output = Lut.applyLut(rs, nv21Image.nv21ByteArray, nv21Image.width, nv21Image.height,
                        SampleParams.Lut.negative());
                return Nv21Image.nv21ToBitmap(rs, output, nv21Image.width, nv21Image.height);
            }
        }
    };

    private static ImageProcess lut3dProcess = new ImageProcess() {
        @Override
        public Bitmap processImage(RenderScript rs, Bitmap bitmap, ImageFormat imageFormat) {
            if (imageFormat == ImageFormat.BITMAP)
                return Lut3D.apply3dLut(rs, bitmap, SampleParams.Lut3D.swapRedAndBlueCube());
            else {
                Nv21Image nv21Image = Nv21Image.bitmapToNV21(rs, bitmap);
                byte[] output = Lut3D.apply3dLut(rs, nv21Image.nv21ByteArray, nv21Image.width,
                        nv21Image.height, SampleParams.Lut3D.swapRedAndBlueCube());
                return Nv21Image.nv21ToBitmap(rs, output, nv21Image.width, nv21Image.height);
            }
        }
    };

    private static ImageProcess resizeProcess = new ImageProcess() {
        @Override
        public Bitmap processImage(RenderScript rs, Bitmap bitmap, ImageFormat imageFormat) {
            if (imageFormat == ImageFormat.BITMAP)
                return Resize.resize(rs, bitmap, 50, 50);
            else {
                Nv21Image nv21Image = Nv21Image.bitmapToNV21(rs, bitmap);
                byte[] output = Resize.resize(rs, nv21Image.nv21ByteArray, nv21Image.width,
                        nv21Image.height, 50,50);
                return Nv21Image.nv21ToBitmap(rs, output, 50, 50);
            }
        }
    };

}
