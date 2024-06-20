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

package ome.codecs;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.WritableRaster;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import loci.common.DataTools;
import loci.common.RandomAccessInputStream;
import ome.codecs.gui.AWTImageTools;

import javax.imageio.IIOImage;
import javax.imageio.ImageWriteParam;

import javax.imageio.ImageTypeSpecifier;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.NamedNodeMap;
import javax.imageio.metadata.IIOMetadata;


/**
 * This class implements JPEG compression and decompression.
 */
public class JPEGCodec extends BaseCodec {

  /**
   * The CodecOptions parameter should have the following fields set:
   *  {@link CodecOptions#width width}
   *  {@link CodecOptions#height height}
   *  {@link CodecOptions#channels channels}
   *  {@link CodecOptions#bitsPerSample bitsPerSample}
   *  {@link CodecOptions#interleaved interleaved}
   *  {@link CodecOptions#littleEndian littleEndian}
   *  {@link CodecOptions#signed signed}
   *
   * @see Codec#compress(byte[], CodecOptions)
   */
  @Override
  public byte[] compress(byte[] data, CodecOptions options)
    throws CodecException
  {
    if (data == null || data.length == 0) return data;
    if (options == null) options = CodecOptions.getDefaultOptions();

    if (options.bitsPerSample > 8) {
      throw new CodecException("> 8 bit data cannot be compressed with JPEG.");
    }

    int maxByteArrayLength = Integer.MAX_VALUE / 8;
    ByteArrayOutputStream out = new ByteArrayOutputStream(
            Math.min(maxByteArrayLength, Math.max(1024, data.length / 4)));
    BufferedImage img = AWTImageTools.makeImage(data, options.width,
      options.height, options.channels, options.interleaved,
      options.bitsPerSample / 8, false, options.littleEndian, options.signed);

    try {
      // Adjustable jpeg quality
      // How to use:
      // Set jpegquality using CodecOptions in the calling object (e.g. QuPath using OMEPyramidWriter()):
      //   CodecOptions options = new CodecOptions();
      //   options.quality = 0.95;
      //   writer.setCodecOptions(options)

      double jpegquality = 0.75;
      if (options.quality > 0) {
        jpegquality = options.quality;
      }
      if (jpegquality < 0.25) {
        LOGGER.warn("JPEG quality {} too small, using 0.25", jpegquality);
      }
      else if (jpegquality > 1) {
        LOGGER.warn("JPEG quality {} too large, using 1.0", jpegquality);
      }
      jpegquality = Math.max(0.25, Math.min(1.0, jpegquality));

      // Disable chroma subsampling
      // How to use:
      // Set disableChromaSubsampling using CodecOptions in the calling object (e.g. QuPath using OMEPyramidWriter()):
      //   CodecOptions options = new CodecOptions();
      //   options.disableChromaSubsampling = true;
      //   writer.setCodecOptions(options)
      boolean disableChromaSubsampling = jpegquality>=0.9; 
      if (options.disableChromaSubsampling) {
        disableChromaSubsampling = options.disableChromaSubsampling;
      }
	
      ImageWriter jpgWriter = ImageIO.getImageWritersByFormatName("jpg").next();
      if (jpgWriter == null) {
        return null;
      }
      ImageWriteParam jpgWriteParam = jpgWriter.getDefaultWriteParam();
      jpgWriteParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
      jpgWriteParam.setCompressionQuality((float) jpegquality);
      if (jpegquality == 1.0)
        jpgWriteParam.setSourceSubsampling(1, 1, 0, 0);
      
      ImageOutputStream stream = new MemoryCacheImageOutputStream(out);
      try {
        Iterator<ImageWriter> iterator = ImageIO.getImageWritersByFormatName("jpeg");
    	if (iterator.hasNext()) {
	  ImageWriter writer = iterator.next();
	  writer.setOutput(stream);

         IIOImage outputImage = null;
         if (!disableChromaSubsampling)  // Use chroma subsampling YUV420
           outputImage = new IIOImage(img, null, null);
         else
           outputImage = createIIOImageNoChromaSubsampling(jpgWriter, img, jpgWriteParam);
		
	  writer.write(null, outputImage, jpgWriteParam);
        }
      } finally {
        jpgWriter.dispose();
        stream.flush(); 	      
        stream.close();
      }          
    }
    catch (IOException e) {
      throw new CodecException("Could not write JPEG data", e);
    }
    return out.toByteArray();
  }

  /**
   * Create an IIOImage object with disabled chroma subsampling.
   */
  private IIOImage createIIOImageNoChromaSubsampling(ImageWriter jpgWriter, BufferedImage img, ImageWriteParam jpgWriteParam)
    throws CodecException {
    // Disable JPEG chroma subsampling
    // http://svn.apache.org/repos/asf/shindig/trunk/java/gadgets/src/main/java/org/apache/shindig/gadgets/rewrite/image/BaseOptimizer.java
    // http://svn.apache.org/repos/asf/shindig/trunk/java/gadgets/src/main/java/org/apache/shindig/gadgets/rewrite/image/JpegImageUtils.java  
    try {
      IIOMetadata metadata = jpgWriter.getDefaultImageMetadata(new ImageTypeSpecifier(img.getColorModel(), img.getSampleModel()), jpgWriteParam);
      Node rootNode = metadata!=null ? metadata.getAsTree("javax_imageio_jpeg_image_1.0") : null;
      boolean metadataUpdated = false;
      // The top level root node has two children, out of which the second one will
      // contain all the information related to image markers.
      if (rootNode!=null && rootNode.getLastChild() != null) {
        Node markerNode = rootNode.getLastChild();
        NodeList markers = markerNode.getChildNodes();
        // Search for 'SOF' marker where subsampling information is stored.
        for (int i = 0; i < markers.getLength(); i++) {
          Node node = markers.item(i);
          // 'SOF' marker can have
          //   1 child node if the color representation is greyscale,
          //   3 child nodes if the color representation is YCbCr, and
          //   4 child nodes if the color representation is YCMK.
          // This subsampling applies only to YCbCr.
          if (node.getNodeName().equalsIgnoreCase("sof") && node.hasChildNodes() && node.getChildNodes().getLength() == 3) {
            // In 'SOF' marker, first child corresponds to the luminance channel, and setting
            // the HsamplingFactor and VsamplingFactor to 1, will imply 4:4:4 chroma subsampling.
            NamedNodeMap attrMap = node.getFirstChild().getAttributes();
            // SamplingModes: UNKNOWN(-2), DEFAULT(-1), YUV444(17), YUV422(33), YUV420(34), YUV411(65)
            int samplingmode = 17;   // YUV444
            attrMap.getNamedItem("HsamplingFactor").setNodeValue((samplingmode & 0xf) + "");
            attrMap.getNamedItem("VsamplingFactor").setNodeValue(((samplingmode >> 4) & 0xf) + "");
            metadataUpdated = true;
            break;
          }
        }
      }
      // Read the updated metadata from the metadata node tree.
      if (metadataUpdated)
        metadata.setFromTree("javax_imageio_jpeg_image_1.0", rootNode);

      IIOImage iioImage = new IIOImage(img, null, metadata);
      
      return iioImage;
    }
    catch (IOException e) {
        throw new CodecException("Could not create IIOImage object with disbaled chroma subsampling", e);
      }
  }
	
  /**
   * The CodecOptions parameter should have the following fields set:
   *  {@link CodecOptions#interleaved interleaved}
   *  {@link CodecOptions#littleEndian littleEndian}
   *
   * @see Codec#decompress(RandomAccessInputStream, CodecOptions)
   */
  @Override
  public byte[] decompress(RandomAccessInputStream in, CodecOptions options)
    throws CodecException, IOException
  {
    BufferedImage b = null;
    long fp = in.getFilePointer();
    try {
      try {
        while (in.read() != (byte) 0xff || in.read() != (byte) 0xd8);
        in.seek(in.getFilePointer() - 2);
      }
      catch (EOFException e) {
        in.seek(fp);
      }

      ImageInputStream stream = new MemoryCacheImageInputStream(new BufferedInputStream(in, 81920));
      b = ImageIO.read(stream);
      if (b == null)
    	  throw new NullPointerException("ImageIO returned null when reading JPEG stream");
    }
    catch (IOException exc) {
        in.seek(fp);
        return new LosslessJPEGCodec().decompress(in, options);
    }

    if (options == null) options = CodecOptions.getDefaultOptions();

    int nPixels = b.getWidth() * b.getHeight();
    WritableRaster r = (WritableRaster) b.getRaster();
    if (!options.ycbcr && r.getDataBuffer() instanceof DataBufferByte &&
      b.getType() == BufferedImage.TYPE_BYTE_GRAY)
    {
      DataBufferByte bb = (DataBufferByte) r.getDataBuffer();

      if (bb.getNumBanks() == 1) {
        byte[] raw = bb.getData();
        if (options.interleaved || bb.getSize() == nPixels) {
          return raw;
        }
      }
    }

    byte[][] buf = AWTImageTools.getPixelBytes(b, options.littleEndian);

    // correct for YCbCr encoding, if necessary
    if (options.ycbcr && buf.length == 3) {
      int n = buf[0].length;
      int nBytes = n / (b.getWidth() * b.getHeight());
      int mask = (int) (Math.pow(2, nBytes * 8) - 1);
      for (int i=0; i<n; i+=nBytes) {
        double y = DataTools.bytesToInt(buf[0], i, nBytes, options.littleEndian);
        double cb = DataTools.bytesToInt(buf[1], i, nBytes, options.littleEndian);
        double cr = DataTools.bytesToInt(buf[2], i, nBytes, options.littleEndian);

        cb = Math.max(0, cb - 128);
        cr = Math.max(0, cr - 128);

        int red = (int) (y + 1.402 * cr);
        int green = (int) (y - 0.34414 * cb - 0.71414 * cr);
        int blue = (int) (y + 1.772 * cb);

        red = (int) (Math.min(red, mask)) & mask;
        green = (int) (Math.min(green, mask)) & mask;
        blue = (int) (Math.min(blue, mask)) & mask;

        DataTools.unpackBytes(red, buf[0], i, nBytes, options.littleEndian);
        DataTools.unpackBytes(green, buf[1], i, nBytes, options.littleEndian);
        DataTools.unpackBytes(blue, buf[2], i, nBytes, options.littleEndian);
      }
    }

    byte[] rtn = new byte[buf.length * buf[0].length];
    if (buf.length == 1) rtn = buf[0];
    else {
      if (options.interleaved) {
    	  int channels = buf.length;
    	  for (int j=0; j<channels; j++) {
    		  byte[] bufChannel = buf[j];
    		  int n = bufChannel.length;
    		  int next = j;
    		  for (int i=0; i<n; i++) {
    			  rtn[next] = bufChannel[i];
    			  next += channels;
    		  }
    	  }
      }
      else {
        for (int i=0; i<buf.length; i++) {
          System.arraycopy(buf[i], 0, rtn, i*buf[0].length, buf[i].length);
        }
      }
    }
    return rtn;
  }
}
