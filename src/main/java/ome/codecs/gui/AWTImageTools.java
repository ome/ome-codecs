/*
 * #%L
 * Image encoding and decoding routines.
 * %%
 * Copyright (C) 2005 - 2017 Open Microscopy Environment:
 *   - Board of Regents of the University of Wisconsin-Madison
 *   - Glencoe Software, Inc.
 *   - University of Dundee
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

package ome.codecs.gui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.MediaTracker;
import java.awt.RenderingHints;
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.geom.AffineTransform;
import java.awt.image.BandedSampleModel;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.ComponentSampleModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferDouble;
import java.awt.image.DataBufferFloat;
import java.awt.image.DataBufferInt;
import java.awt.image.DataBufferUShort;
import java.awt.image.DirectColorModel;
import java.awt.image.IndexColorModel;
import java.awt.image.PixelInterleavedSampleModel;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.awt.image.SampleModel;
import java.awt.image.SinglePixelPackedSampleModel;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.util.Hashtable;

import loci.common.DataTools;
import ome.codecs.CodecException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A utility class with convenience methods for manipulating images
 * in {@link java.awt.image.BufferedImage} form.
 *
 * To work with images in primitive array form,
 * use the {@link ome.codecs.ImageTools} class.
 *
 * Much code was stolen and adapted from
 * <a href="http://forum.java.sun.com/thread.jspa?threadID=522483">
 * DrLaszloJamf's posts</a>
 * on the Java forums.
 *
 * @author Curtis Rueden ctrueden at wisc.edu
 */
public final class AWTImageTools {

  // -- Constants --

  /** ImageObserver for working with AWT images. */
  protected static final Component OBS = new Container();

  private static final Logger LOGGER =
    LoggerFactory.getLogger(AWTImageTools.class);

  // -- Constructor --

  private AWTImageTools() { }

  // -- Image construction - from 1D (single channel) data arrays --

  /**
   * Creates an image from the given single-channel byte data.
   *
   * @param data Array containing image data.
   * @param w Width of image plane.
   * @param h Height of image plane.
   * @param signed Whether the byte values should be treated as signed
   *   (-128 to 127) instead of unsigned (0 to 255).
   */
  public static BufferedImage makeImage(byte[] data,
    int w, int h, boolean signed)
  {
    return makeImage(new byte[][] {data}, w, h, signed);
  }

  /**
   * Creates an image from the given single-channel short data.
   *
   * @param data Array containing image data.
   * @param w Width of image plane.
   * @param h Height of image plane.
   * @param signed Whether the short values should be treated as signed
   *   (-32768 to 32767) instead of unsigned (0 to 65535).
   */
  public static BufferedImage makeImage(short[] data,
    int w, int h, boolean signed)
  {
    return makeImage(new short[][] {data}, w, h, signed);
  }

  /**
   * Creates an image from the given single-channel int data.
   *
   * @param data Array containing image data.
   * @param w Width of image plane.
   * @param h Height of image plane.
   * @param signed Whether the int values should be treated as signed
   *   (-2^31 to 2^31-1) instead of unsigned (0 to 2^32-1).
   */
  public static BufferedImage makeImage(int[] data,
    int w, int h, boolean signed)
  {
    return makeImage(new int[][] {data}, w, h, signed);
  }

  /**
   * Creates an image from the given single-channel float data.
   *
   * @param data Array containing image data.
   * @param w Width of image plane.
   * @param h Height of image plane.
   */
  public static BufferedImage makeImage(float[] data, int w, int h) {
    return makeImage(new float[][] {data}, w, h);
  }

  /**
   * Creates an image from the given single-channel double data.
   *
   * @param data Array containing image data.
   * @param w Width of image plane.
   * @param h Height of image plane.
   */
  public static BufferedImage makeImage(double[] data, int w, int h) {
    return makeImage(new double[][] {data}, w, h);
  }

  // -- Image construction - from 1D (interleaved or banded) data arrays --

  /**
   * Creates an image from the given byte data.
   *
   * @param data Array containing image data.
   * @param w Width of image plane.
   * @param h Height of image plane.
   * @param c Number of channels.
   * @param interleaved If set, the channels are assumed to be interleaved;
   *   otherwise they are assumed to be sequential.
   *   For example, for RGB data, the pattern "RGBRGBRGB..." is interleaved,
   *   while "RRR...GGG...BBB..." is sequential.
   * @param signed Whether the byte values should be treated as signed
   *   (-128 to 127) instead of unsigned (0 to 255).
   */
  public static BufferedImage makeImage(byte[] data,
    int w, int h, int c, boolean interleaved, boolean signed)
  {
    if (c == 1) return makeImage(data, w, h, signed);
    if (c > 2) return makeRGBImage(data, c, w, h, interleaved);
    int dataType;
    DataBuffer buffer;
    dataType = DataBuffer.TYPE_BYTE;
    if (signed) {
      buffer = new SignedByteBuffer(data, c * w * h);
    }
    else {
      buffer = new DataBufferByte(data, c * w * h);
    }
    return constructImage(c, dataType, w, h, interleaved, false, buffer);
  }

  /**
   * Creates an image from the given short data.
   *
   * @param data Array containing image data.
   * @param w Width of image plane.
   * @param h Height of image plane.
   * @param c Number of channels.
   * @param interleaved If set, the channels are assumed to be interleaved;
   *   otherwise they are assumed to be sequential.
   *   For example, for RGB data, the pattern "RGBRGBRGB..." is interleaved,
   *   while "RRR...GGG...BBB..." is sequential.
   * @param signed Whether the short values should be treated as signed
   *   (-32768 to 32767) instead of unsigned (0 to 65535).
   */
  public static BufferedImage makeImage(short[] data,
    int w, int h, int c, boolean interleaved, boolean signed)
  {
    if (c == 1) return makeImage(data, w, h, signed);
    int dataType;
    DataBuffer buffer;
    if (signed) {
      dataType = DataBuffer.TYPE_SHORT;
      buffer = new SignedShortBuffer(data, c * w * h);
    }
    else {
      dataType = DataBuffer.TYPE_USHORT;
      buffer = new DataBufferUShort(data, c * w * h);
    }
    return constructImage(c, dataType, w, h, interleaved, false, buffer);
  }

  /**
   * Creates an image from the given int data.
   *
   * @param data Array containing image data.
   * @param w Width of image plane.
   * @param h Height of image plane.
   * @param c Number of channels.
   * @param interleaved If set, the channels are assumed to be interleaved;
   *   otherwise they are assumed to be sequential.
   *   For example, for RGB data, the pattern "RGBRGBRGB..." is interleaved,
   *   while "RRR...GGG...BBB..." is sequential.
   * @param signed Whether the int values should be treated as signed
   *   (-2^31 to 2^31-1) instead of unsigned (0 to 2^32-1).
   */
  public static BufferedImage makeImage(int[] data,
    int w, int h, int c, boolean interleaved, boolean signed)
  {
    if (c == 1) return makeImage(data, w, h, signed);
    int dataType = DataBuffer.TYPE_INT;
    DataBuffer buffer;
    if (signed) {
      buffer = new DataBufferInt(data, c * w * h);
    }
    else {
      buffer = new UnsignedIntBuffer(data, c * w * h);
    }
    return constructImage(c, dataType, w, h, interleaved, false, buffer);
  }

  /**
   * Creates an image from the given float data.
   *
   * @param data Array containing image data.
   * @param w Width of image plane.
   * @param h Height of image plane.
   * @param c Number of channels.
   * @param interleaved If set, the channels are assumed to be interleaved;
   *   otherwise they are assumed to be sequential.
   *   For example, for RGB data, the pattern "RGBRGBRGB..." is interleaved,
   *   while "RRR...GGG...BBB..." is sequential.
   */
  public static BufferedImage makeImage(float[] data,
    int w, int h, int c, boolean interleaved)
  {
    if (c == 1) return makeImage(data, w, h);
    int dataType = DataBuffer.TYPE_FLOAT;
    DataBuffer buffer = new DataBufferFloat(data, c * w * h);
    return constructImage(c, dataType, w, h, interleaved, false, buffer);
  }

  /**
   * Creates an image from the given double data.
   *
   * @param data Array containing image data.
   * @param w Width of image plane.
   * @param h Height of image plane.
   * @param c Number of channels.
   * @param interleaved If set, the channels are assumed to be interleaved;
   *   otherwise they are assumed to be sequential.
   *   For example, for RGB data, the pattern "RGBRGBRGB..." is interleaved,
   *   while "RRR...GGG...BBB..." is sequential.
   */
  public static BufferedImage makeImage(double[] data,
    int w, int h, int c, boolean interleaved)
  {
    if (c == 1) return makeImage(data, w, h);
    int dataType = DataBuffer.TYPE_DOUBLE;
    DataBuffer buffer = new DataBufferDouble(data, c * w * h);
    return constructImage(c, dataType, w, h, interleaved, false, buffer);
  }

  // -- Image construction - from 2D (banded) data arrays --

  /**
   * Creates an image from the given byte data.
   *
   * @param data Array containing image data.
   *   It is assumed that each channel corresponds to one element of the array.
   *   For example, for RGB data, data[0] is R, data[1] is G, and data[2] is B.
   * @param w Width of image plane.
   * @param h Height of image plane.
   * @param signed Whether the byte values should be treated as signed
   *   (-128 to 127) instead of unsigned (0 to 255).
   */
  public static BufferedImage makeImage(byte[][] data,
    int w, int h, boolean signed)
  {
    if (data.length > 2) return makeRGBImage(data, w, h);
    int dataType;
    DataBuffer buffer;
    dataType = DataBuffer.TYPE_BYTE;
    if (signed) {
      buffer = new SignedByteBuffer(data, data[0].length);
    }
    else {
      buffer = new DataBufferByte(data, data[0].length);
    }
    return constructImage(data.length, dataType, w, h, false, true, buffer);
  }

  /**
   * Creates an image from the given short data.
   *
   * @param data Array containing image data.
   *   It is assumed that each channel corresponds to one element of the array.
   *   For example, for RGB data, data[0] is R, data[1] is G, and data[2] is B.
   * @param w Width of image plane.
   * @param h Height of image plane.
   * @param signed Whether the short values should be treated as signed
   *   (-32768 to 32767) instead of unsigned (0 to 65535).
   */
  public static BufferedImage makeImage(short[][] data,
    int w, int h, boolean signed)
  {
    int dataType;
    DataBuffer buffer;
    if (signed) {
      dataType = DataBuffer.TYPE_SHORT;
      buffer = new SignedShortBuffer(data, data[0].length);
    }
    else {
      dataType = DataBuffer.TYPE_USHORT;
      buffer = new DataBufferUShort(data, data[0].length);
    }
    return constructImage(data.length, dataType, w, h, false, true, buffer);
  }

  /**
   * Creates an image from the given int data.
   *
   * @param data Array containing image data.
   *   It is assumed that each channel corresponds to one element of the array.
   *   For example, for RGB data, data[0] is R, data[1] is G, and data[2] is B.
   * @param w Width of image plane.
   * @param h Height of image plane.
   * @param signed Whether the int values should be treated as signed
   *   (-2^31 to 2^31-1) instead of unsigned (0 to 2^32-1).
   */
  public static BufferedImage makeImage(int[][] data,
    int w, int h, boolean signed)
  {
    int dataType = DataBuffer.TYPE_INT;
    DataBuffer buffer;
    if (signed) {
      buffer = new DataBufferInt(data, data[0].length);
    }
    else {
      buffer = new UnsignedIntBuffer(data, data[0].length);
    }
    return constructImage(data.length, dataType, w, h, false, true, buffer);
  }

  /**
   * Creates an image from the given single-precision floating point data.
   *
   * @param data Array containing image data.
   *   It is assumed that each channel corresponds to one element of the array.
   *   For example, for RGB data, data[0] is R, data[1] is G, and data[2] is B.
   * @param w Width of image plane.
   * @param h Height of image plane.
   */
  public static BufferedImage makeImage(float[][] data, int w, int h) {
    int dataType = DataBuffer.TYPE_FLOAT;
    DataBuffer buffer = new DataBufferFloat(data, data[0].length);
    return constructImage(data.length, dataType, w, h, false, true, buffer);
  }

  /**
   * Creates an image from the given double-precision floating point data.
   *
   * @param data Array containing image data.
   *   It is assumed that each channel corresponds to one element of the array.
   *   For example, for RGB data, data[0] is R, data[1] is G, and data[2] is B.
   * @param w Width of image plane.
   * @param h Height of image plane.
   */
  public static BufferedImage makeImage(double[][] data, int w, int h) {
    int dataType = DataBuffer.TYPE_DOUBLE;
    DataBuffer buffer = new DataBufferDouble(data, data[0].length);
    return constructImage(data.length, dataType, w, h, false, true, buffer);
  }

  // -- Image construction - with type conversion --

  /**
   * Creates an image from the given raw byte array,
   * performing any necessary type conversions.
   *
   * @param data Array containing image data.
   * @param w Width of image plane.
   * @param h Height of image plane.
   * @param c Number of channels.
   * @param interleaved If set, the channels are assumed to be interleaved;
   *   otherwise they are assumed to be sequential.
   *   For example, for RGB data, the pattern "RGBRGBRGB..." is interleaved,
   *   while "RRR...GGG...BBB..." is sequential.
   * @param bpp Denotes the number of bytes in the returned primitive type
   *   (e.g. if bpp == 2, we should return an array of type short).
   * @param fp If set and bpp == 4 or bpp == 8, then return floats or doubles.
   * @param little Whether byte array is in little-endian order.
   * @param signed Whether the data values should be treated as signed
   *   instead of unsigned.
   */
  public static BufferedImage makeImage(byte[] data, int w, int h, int c,
    boolean interleaved, int bpp, boolean fp, boolean little, boolean signed)
  {
    Object pixels = DataTools.makeDataArray(data,
      bpp % 3 == 0 ? bpp / 3 : bpp, fp, little);

    if (pixels instanceof byte[]) {
      return makeImage((byte[]) pixels, w, h, c, interleaved, signed);
    }
    else if (pixels instanceof short[]) {
      return makeImage((short[]) pixels, w, h, c, interleaved, signed);
    }
    else if (pixels instanceof int[]) {
      return makeImage((int[]) pixels, w, h, c, interleaved, signed);
    }
    else if (pixels instanceof float[]) {
      return makeImage((float[]) pixels, w, h, c, interleaved);
    }
    else if (pixels instanceof double[]) {
      return makeImage((double[]) pixels, w, h, c, interleaved);
    }
    return null;
  }

  /**
   * Creates an image from the given raw byte array,
   * performing any necessary type conversions.
   *
   * @param data Array containing image data, one channel per element.
   * @param w Width of image plane.
   * @param h Height of image plane.
   * @param bpp Denotes the number of bytes in the returned primitive type
   *   (e.g. if bpp == 2, we should return an array of type short).
   * @param fp If set and bpp == 4 or bpp == 8, then return floats or doubles.
   * @param little Whether byte array is in little-endian order.
   * @param signed Whether the data values should be treated as signed
   *   instead of unsigned.
   */
  public static BufferedImage makeImage(byte[][] data,
    int w, int h, int bpp, boolean fp, boolean little, boolean signed)
  {
    int c = data.length;
    Object v = null;
    for (int i=0; i<c; i++) {
      Object pixels = DataTools.makeDataArray(data[i],
        bpp % 3 == 0 ? bpp / 3 : bpp, fp, little);
      if (pixels instanceof byte[]) {
        if (v == null) v = new byte[c][];
        ((byte[][]) v)[i] = (byte[]) pixels;
      }
      else if (pixels instanceof short[]) {
        if (v == null) v = new short[c][];
        ((short[][]) v)[i] = (short[]) pixels;
      }
      else if (pixels instanceof int[]) {
        if (v == null) v = new int[c][];
        ((int[][]) v)[i] = (int[]) pixels;
      }
      else if (pixels instanceof float[]) {
        if (v == null) v = new float[c][];
        ((float[][]) v)[i] = (float[]) pixels;
      }
      else if (pixels instanceof double[]) {
        if (v == null) v = new double[c][];
        ((double[][]) v)[i] = (double[]) pixels;
      }
    }
    if (v instanceof byte[][]) {
      return makeImage((byte[][]) v, w, h, signed);
    }
    else if (v instanceof short[][]) {
      return makeImage((short[][]) v, w, h, signed);
    }
    else if (v instanceof int[][]) {
      return makeImage((int[][]) v, w, h, signed);
    }
    else if (v instanceof float[][]) {
      return makeImage((float[][]) v, w, h);
    }
    else if (v instanceof double[][]) {
      return makeImage((double[][]) v, w, h);
    }
    return null;
  }

  public static BufferedImage makeRGBImage(byte[] data, int c, int w, int h,
    boolean interleaved)
  {
    int cc = Math.min(c, 4); // throw away channels beyond 4
    int[] buf = new int[data.length / c];
    int nBits = (cc - 1) * 8;

    for (int i=0; i<buf.length; i++) {
      for (int q=0; q<cc; q++) {
        if (interleaved) {
          buf[i] |= ((data[i*c + q] & 0xff) << (nBits - q*8));
        }
        else {
          buf[i] |= ((data[q*buf.length + i] & 0xff) << (nBits - q*8));
        }
      }
    }

    DataBuffer buffer = new DataBufferInt(buf, buf.length);
    return constructImage(cc, DataBuffer.TYPE_INT, w, h, false, false, buffer);
  }

  public static BufferedImage makeRGBImage(byte[][] data, int w, int h) {
    int[] buf = new int[data[0].length];
    int nBits = (data.length - 1) * 8;

    for (int i=0; i<buf.length; i++) {
      for (int q=0; q<data.length; q++) {
        buf[i] |= ((data[q][i] & 0xff) << (nBits - q*8));
      }
    }

    DataBuffer buffer = new DataBufferInt(buf, buf.length);
    return constructImage(data.length, DataBuffer.TYPE_INT, w, h, false,
      false, buffer);
  }

  // -- Image construction - miscellaneous --

  /** Creates an image with the given DataBuffer. */
  public static BufferedImage constructImage(int c, int type, int w,
    int h, boolean interleaved, boolean banded, DataBuffer buffer)
  {
    return constructImage(c, type, w, h, interleaved, banded, buffer, null);
  }

  /** Creates an image with the given DataBuffer. */
  public static BufferedImage constructImage(int c, int type, int w,
    int h, boolean interleaved, boolean banded, DataBuffer buffer,
    ColorModel colorModel)
  {
    if (c > 4) {
      throw new IllegalArgumentException(
        "Cannot construct image with " + c + " channels");
    }
    if (colorModel == null || colorModel instanceof DirectColorModel) {
      colorModel = makeColorModel(c, type);
      if (colorModel == null) return null;
      if (buffer instanceof UnsignedIntBuffer) {
        try {
          colorModel = new UnsignedIntColorModel(32, type, c);
        }
        catch (IOException e) {
          return null;
        }
      }
    }

    SampleModel model;
    if (c > 2 && type == DataBuffer.TYPE_INT && buffer.getNumBanks() == 1 &&
      !(buffer instanceof UnsignedIntBuffer))
    {
      int[] bitMasks = new int[c];
      for (int i=0; i<c; i++) {
        bitMasks[i] = 0xff << ((c - i - 1) * 8);
      }
      model =
        new SinglePixelPackedSampleModel(DataBuffer.TYPE_INT, w, h, bitMasks);
    }
    else if (banded) model = new BandedSampleModel(type, w, h, c);
    else if (interleaved) {
      int[] bandOffsets = new int[c];
      for (int i=0; i<c; i++) bandOffsets[i] = i;
      model = new PixelInterleavedSampleModel(type,
        w, h, c, c * w, bandOffsets);
    }
    else {
      int[] bandOffsets = new int[c];
      for (int i=0; i<c; i++) bandOffsets[i] = i * w * h;
      model = new ComponentSampleModel(type, w, h, 1, w, bandOffsets);
    }

    WritableRaster raster = Raster.createWritableRaster(model, buffer, null);

    BufferedImage b = null;

    if (c == 1 && type == DataBuffer.TYPE_BYTE &&
      !(buffer instanceof SignedByteBuffer))
    {
      if (colorModel instanceof IndexColorModel) {
        b = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_INDEXED);
      }
      else {
        b = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
      }
      b.setData(raster);
    }
    else if (c == 1 && type == DataBuffer.TYPE_USHORT) {
      if (!(colorModel instanceof IndexColorModel)) {
        b = new BufferedImage(w, h, BufferedImage.TYPE_USHORT_GRAY);
        b.setData(raster);
      }
    }
    else if (c > 2 && type == DataBuffer.TYPE_INT && buffer.getNumBanks() == 1
      && !(buffer instanceof UnsignedIntBuffer))
    {
      if (c == 3) {
        b = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
      }
      else if (c == 4) {
        b = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
      }

      if (b != null) b.setData(raster);
    }

    if (b == null) b = new BufferedImage(colorModel, raster, false, null);

    return b;
  }

  // -- Data extraction --

  /**
   * Gets the image's pixel data as arrays of primitives, one per channel.
   * The returned type will be either byte[][], short[][], int[][], float[][]
   * or double[][], depending on the image's transfer type.
   */
  public static Object getPixels(BufferedImage image) {
    return getPixels(image, 0, 0, image.getWidth(), image.getHeight());
  }

  /**
   *
   * Gets the image's pixel data as arrays of primitives, one per channel.
   * The returned type will be either byte[][], short[][], int[][], float[][]
   * or double[][], depending on the image's transfer type.
   */
  public static Object getPixels(BufferedImage image, int x, int y,
    int w, int h)
  {
    WritableRaster raster = image.getRaster();
    return getPixels(raster, x, y, w, h);
  }

  /**
   * Gets the raster's pixel data as arrays of primitives, one per channel.
   * The returned type will be either byte[][], short[][], int[][], float[][]
   * or double[][], depending on the raster's transfer type.
   */
  public static Object getPixels(WritableRaster raster) {
    return getPixels(raster, 0, 0, raster.getWidth(), raster.getHeight());
  }

  /**
   * Gets the raster's pixel data as arrays of primitives, one per channel.
   * The returned type will be either byte[][], short[][], int[][], float[][]
   * or double[][], depending on the raster's transfer type.
   */
  public static Object getPixels(WritableRaster raster, int x, int y,
    int w, int h)
  {
    int tt = raster.getTransferType();
    if (tt == DataBuffer.TYPE_BYTE) return getBytes(raster, x, y, w, h);
    else if (tt == DataBuffer.TYPE_USHORT || tt == DataBuffer.TYPE_SHORT) {
      return getShorts(raster, x, y, w, h);
    }
    else if (tt == DataBuffer.TYPE_INT) return getInts(raster, x, y, w, h);
    else if (tt == DataBuffer.TYPE_FLOAT) return getFloats(raster, x, y, w, h);
    else if (tt == DataBuffer.TYPE_DOUBLE) {
      return getDoubles(raster, x, y, w, h);
    }
    else return null;
  }

  /** Extracts pixel data as arrays of unsigned bytes, one per channel. */
  public static byte[][] getBytes(BufferedImage image) {
    WritableRaster r = image.getRaster();
    return getBytes(r);
  }

  /** Extracts pixel data as arrays of unsigned bytes, one per channel. */
  public static byte[][] getBytes(WritableRaster r) {
    return getBytes(r, 0, 0, r.getWidth(), r.getHeight());
  }

  /** Extracts pixel data as arrays of unsigned bytes, one per channel. */
  public static byte[][] getBytes(WritableRaster r, int x, int y, int w, int h)
  {
    if (canUseBankDataDirectly(r, DataBuffer.TYPE_BYTE, DataBufferByte.class) &&
      x == 0 && y == 0 && w == r.getWidth() && h == r.getHeight())
    {
      return ((DataBufferByte) r.getDataBuffer()).getBankData();
    }
    int c = r.getNumBands();
    byte[][] samples = new byte[c][w * h];
    int[] buf = new int[w * h];
    for (int i=0; i<c; i++) {
      r.getSamples(x, y, w, h, i, buf);
      for (int j=0; j<buf.length; j++) samples[i][j] = (byte) buf[j];
    }
    return samples;
  }

  /** Extracts pixel data as arrays of unsigned shorts, one per channel. */
  public static short[][] getShorts(BufferedImage image) {
    WritableRaster r = image.getRaster();
    return getShorts(r);
  }

  /** Extracts pixel data as arrays of unsigned shorts, one per channel. */
  public static short[][] getShorts(WritableRaster r) {
    return getShorts(r, 0, 0, r.getWidth(), r.getHeight());
  }

  /** Extracts pixel data as arrays of unsigned shorts, one per channel. */
  public static short[][] getShorts(WritableRaster r, int x, int y,
    int w, int h)
  {
    if (canUseBankDataDirectly(r,
      DataBuffer.TYPE_USHORT, DataBufferUShort.class) && x == 0 && y == 0 &&
      w == r.getWidth() && h == r.getHeight())
    {
      return ((DataBufferUShort) r.getDataBuffer()).getBankData();
    }
    int c = r.getNumBands();
    short[][] samples = new short[c][w * h];
    int[] buf = new int[w * h];
    for (int i=0; i<c; i++) {
      r.getSamples(x, y, w, h, i, buf);
      for (int j=0; j<buf.length; j++) samples[i][j] = (short) buf[j];
    }
    return samples;
  }

  /** Extracts pixel data as arrays of signed integers, one per channel. */
  public static int[][] getInts(BufferedImage image) {
    WritableRaster r = image.getRaster();
    return getInts(r);
  }

  /** Extracts pixel data as arrays of signed integers, one per channel. */
  public static int[][] getInts(WritableRaster r) {
    return getInts(r, 0, 0, r.getWidth(), r.getHeight());
  }

  /** Extracts pixel data as arrays of signed integers, one per channel. */
  public static int[][] getInts(WritableRaster r, int x, int y, int w, int h) {
    if (canUseBankDataDirectly(r, DataBuffer.TYPE_INT, DataBufferInt.class) &&
      x == 0 && y == 0 && w == r.getWidth() && h == r.getHeight())
    {
      return ((DataBufferInt) r.getDataBuffer()).getBankData();
    }
    // NB: an order of magnitude faster than the naive makeType solution
    int c = r.getNumBands();
    int[][] samples = new int[c][w * h];
    for (int i=0; i<c; i++) r.getSamples(x, y, w, h, i, samples[i]);
    return samples;
  }

  /** Extracts pixel data as arrays of floats, one per channel. */
  public static float[][] getFloats(BufferedImage image) {
    WritableRaster r = image.getRaster();
    return getFloats(r);
  }

  /** Extracts pixel data as arrays of floats, one per channel. */
  public static float[][] getFloats(WritableRaster r) {
    return getFloats(r, 0, 0, r.getWidth(), r.getHeight());
  }

  /** Extracts pixel data as arrays of floats, one per channel. */
  public static float[][] getFloats(WritableRaster r, int x, int y,
    int w, int h)
  {
    if (canUseBankDataDirectly(r, DataBuffer.TYPE_FLOAT,
      DataBufferFloat.class) && x == 0 && y == 0 && w == r.getWidth() &&
      h == r.getHeight())
    {
      return ((DataBufferFloat) r.getDataBuffer()).getBankData();
    }
    // NB: an order of magnitude faster than the naive makeType solution
    int c = r.getNumBands();
    float[][] samples = new float[c][w * h];
    for (int i=0; i<c; i++) r.getSamples(x, y, w, h, i, samples[i]);
    return samples;
  }

  /** Extracts pixel data as arrays of doubles, one per channel. */
  public static double[][] getDoubles(BufferedImage image) {
    WritableRaster r = image.getRaster();
    return getDoubles(r);
  }

  /** Extracts pixel data as arrays of doubles, one per channel. */
  public static double[][] getDoubles(WritableRaster r) {
    return getDoubles(r, 0, 0, r.getWidth(), r.getHeight());
  }

  /** Extracts pixel data as arrays of doubles, one per channel. */
  public static double[][] getDoubles(WritableRaster r, int x, int y,
    int w, int h)
  {
    if (canUseBankDataDirectly(r,
      DataBuffer.TYPE_DOUBLE, DataBufferDouble.class) && x == 0 && y == 0 &&
      w == r.getWidth() && h == r.getHeight())
    {
      return ((DataBufferDouble) r.getDataBuffer()).getBankData();
    }
    // NB: an order of magnitude faster than the naive makeType solution
    int c = r.getNumBands();
    double[][] samples = new double[c][w * h];
    for (int i=0; i<c; i++) r.getSamples(x, y, w, h, i, samples[i]);
    return samples;
  }

  /**
   * Whether we can return the data buffer's bank data
   * without performing any copy or conversion operations.
   */
  private static boolean canUseBankDataDirectly(WritableRaster r,
    int transferType, Class<? extends DataBuffer> dataBufferClass)
  {
    int tt = r.getTransferType();
    if (tt != transferType) return false;
    DataBuffer buffer = r.getDataBuffer();
    if (!dataBufferClass.isInstance(buffer)) return false;
    SampleModel model = r.getSampleModel();
    if (!(model instanceof ComponentSampleModel)) return false;
    ComponentSampleModel csm = (ComponentSampleModel) model;
    int pixelStride = csm.getPixelStride();
    if (pixelStride != 1) return false;
    int w = r.getWidth();
    int scanlineStride = csm.getScanlineStride();
    if (scanlineStride != w) return false;
    int c = r.getNumBands();
    int[] bandOffsets = csm.getBandOffsets();
    if (bandOffsets.length != c) return false;
    for (int i=0; i<bandOffsets.length; i++) {
      if (bandOffsets[i] != 0) return false;
    }
    for (int i=0; i<bandOffsets.length; i++) {
      if (bandOffsets[i] != i) return false;
    }
    return true;
  }

  /**
   * Return a 2D array of bytes representing the image.  If the transfer type
   * is something other than DataBuffer.TYPE_BYTE, then each pixel value is
   * converted to the appropriate number of bytes.  In other words, if we
   * are given an image with 16-bit data, each channel of the resulting array
   * will have width * height * 2 bytes.
   */
  public static byte[][] getPixelBytes(BufferedImage img, boolean little) {
    return getPixelBytes(img, little, 0, 0, img.getWidth(), img.getHeight());
  }

  /**
   * Return a 2D array of bytes representing the image.  If the transfer type
   * is something other than DataBuffer.TYPE_BYTE, then each pixel value is
   * converted to the appropriate number of bytes.  In other words, if we
   * are given an image with 16-bit data, each channel of the resulting array
   * will have width * height * 2 bytes.
   */
  public static byte[][] getPixelBytes(WritableRaster r, boolean little) {
    return getPixelBytes(r, little, 0, 0, r.getWidth(), r.getHeight());
  }

  /**
   * Return a 2D array of bytes representing the image.  If the transfer type
   * is something other than DataBuffer.TYPE_BYTE, then each pixel value is
   * converted to the appropriate number of bytes.  In other words, if we
   * are given an image with 16-bit data, each channel of the resulting array
   * will have width * height * 2 bytes.
   */
  public static byte[][] getPixelBytes(BufferedImage img, boolean little,
    int x, int y, int w, int h)
  {
    Object pixels = getPixels(img, x, y, w, h);
    int imageType = img.getType();
    byte[][] pixelBytes = null;

    if (pixels instanceof byte[][]) {
      pixelBytes = (byte[][]) pixels;
    }
    else if (pixels instanceof short[][]) {
      short[][] s = (short[][]) pixels;
      pixelBytes = new byte[s.length][s[0].length * 2];
      for (int i=0; i<pixelBytes.length; i++) {
        for (int j=0; j<s[0].length; j++) {
          DataTools.unpackBytes(s[i][j], pixelBytes[i], j * 2, 2, little);
        }
      }
    }
    else if (pixels instanceof int[][]) {
      int[][] in = (int[][]) pixels;

      if (imageType == BufferedImage.TYPE_INT_RGB ||
        imageType == BufferedImage.TYPE_INT_BGR ||
        imageType == BufferedImage.TYPE_INT_ARGB)
      {
        pixelBytes = new byte[in.length][in[0].length];
        for (int c=0; c<in.length; c++) {
          for (int i=0; i<in[0].length; i++) {
            if (imageType != BufferedImage.TYPE_INT_BGR) {
              pixelBytes[c][i] = (byte) (in[c][i] & 0xff);
            }
            else {
              pixelBytes[in.length - c - 1][i] = (byte) (in[c][i] & 0xff);
            }
          }
        }
      }
      else {
        pixelBytes = new byte[in.length][in[0].length * 4];
        for (int i=0; i<pixelBytes.length; i++) {
          for (int j=0; j<in[0].length; j++) {
            DataTools.unpackBytes(in[i][j], pixelBytes[i], j * 4, 4, little);
          }
        }
      }
    }
    else if (pixels instanceof float[][]) {
      float[][] in = (float[][]) pixels;
      pixelBytes = new byte[in.length][in[0].length * 4];
      for (int i=0; i<pixelBytes.length; i++) {
        for (int j=0; j<in[0].length; j++) {
          int v = Float.floatToIntBits(in[i][j]);
          DataTools.unpackBytes(v, pixelBytes[i], j * 4, 4, little);
        }
      }
    }
    else if (pixels instanceof double[][]) {
      double[][] in = (double[][]) pixels;
      pixelBytes = new byte[in.length][in[0].length * 8];
      for (int i=0; i<pixelBytes.length; i++) {
        for (int j=0; j<in[0].length; j++) {
          long v = Double.doubleToLongBits(in[i][j]);
          DataTools.unpackBytes(v, pixelBytes[i], j * 8, 8, little);
        }
      }
    }

    return pixelBytes;
  }

  /**
   * Return a 2D array of bytes representing the image.  If the transfer type
   * is something other than DataBuffer.TYPE_BYTE, then each pixel value is
   * converted to the appropriate number of bytes.  In other words, if we
   * are given an image with 16-bit data, each channel of the resulting array
   * will have width * height * 2 bytes.
   */
  public static byte[][] getPixelBytes(WritableRaster r, boolean little,
    int x, int y, int w, int h)
  {
    Object pixels = getPixels(r);
    byte[][] pixelBytes = null;
    int bpp = 0;

    if (pixels instanceof byte[][]) {
      pixelBytes = (byte[][]) pixels;
      bpp = 1;
    }
    else if (pixels instanceof short[][]) {
      bpp = 2;
      short[][] s = (short[][]) pixels;
      pixelBytes = new byte[s.length][s[0].length * bpp];
      for (int i=0; i<pixelBytes.length; i++) {
        for (int j=0; j<s[0].length; j++) {
          DataTools.unpackBytes(s[i][j], pixelBytes[i], j * bpp, bpp, little);
        }
      }
    }
    else if (pixels instanceof int[][]) {
      bpp = 4;
      int[][] in = (int[][]) pixels;

      pixelBytes = new byte[in.length][in[0].length * bpp];
      for (int i=0; i<pixelBytes.length; i++) {
        for (int j=0; j<in[0].length; j++) {
          DataTools.unpackBytes(in[i][j], pixelBytes[i], j * bpp, bpp, little);
        }
      }
    }
    else if (pixels instanceof float[][]) {
      bpp = 4;
      float[][] in = (float[][]) pixels;
      pixelBytes = new byte[in.length][in[0].length * bpp];
      for (int i=0; i<pixelBytes.length; i++) {
        for (int j=0; j<in[0].length; j++) {
          int v = Float.floatToIntBits(in[i][j]);
          DataTools.unpackBytes(v, pixelBytes[i], j * bpp, bpp, little);
        }
      }
    }
    else if (pixels instanceof double[][]) {
      bpp = 8;
      double[][] in = (double[][]) pixels;
      pixelBytes = new byte[in.length][in[0].length * bpp];
      for (int i=0; i<pixelBytes.length; i++) {
        for (int j=0; j<in[0].length; j++) {
          long v = Double.doubleToLongBits(in[i][j]);
          DataTools.unpackBytes(v, pixelBytes[i], j * bpp, bpp, little);
        }
      }
    }

    if (x == 0 && y == 0 && w == r.getWidth() && h == r.getHeight()) {
      return pixelBytes;
    }

    byte[][] croppedBytes = new byte[pixelBytes.length][w * h * bpp];
    for (int c=0; c<croppedBytes.length; c++) {
      for (int row=0; row<h; row++) {
        int src = (row + y) * r.getWidth() * bpp + x * bpp;
        int dest = row * w * bpp;
        System.arraycopy(pixelBytes[c], src, croppedBytes[c], dest, w * bpp);
      }
    }
    return croppedBytes;
  }

  // -- Image conversion --

  /** Get the bytes from an image, merging the channels as necessary. */
  public static byte[] getBytes(BufferedImage img, boolean separated) {
    byte[][] p = getBytes(img);
    if (separated || p.length == 1) return p[0];

    byte[] rtn = new byte[p.length * p[0].length];
    for (int i=0; i<p.length; i++) {
      System.arraycopy(p[i], 0, rtn, i * p[0].length, p[i].length);
    }
    return rtn;
  }

  // -- Color model --

  /** Gets a color space for the given number of color components. */
  public static ColorSpace makeColorSpace(int c) {
    int type;
    switch (c) {
      case 1:
        type = ColorSpace.CS_GRAY;
        break;
      case 2:
        type = TwoChannelColorSpace.CS_2C;
        break;
      case 3:
        type = ColorSpace.CS_sRGB;
        break;
      case 4:
        type = ColorSpace.CS_sRGB;
        break;
      default:
        return null;
    }
    return TwoChannelColorSpace.getInstance(type);
  }

  /** Gets a color model for the given number of color components. */
  public static ColorModel makeColorModel(int c, int dataType) {
    ColorSpace cs = makeColorSpace(c);
    return cs == null ? null : new ComponentColorModel(cs,
      c == 4, false, Transparency.TRANSLUCENT, dataType);
  }


}
