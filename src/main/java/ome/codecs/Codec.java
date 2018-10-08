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

import java.io.IOException;
import java.nio.ByteBuffer;

import loci.common.RandomAccessInputStream;
import ome.codecs.CodecException;

/**
 * This class is an interface for any kind of compression or decompression.
 * Data is presented to the compressor in a 1D or 2D byte array,
 * with (optionally, depending on the compressor) pixel dimensions and
 * an Object containing any other options the compressor may need.
 *
 * If an argument is not appropriate for the compressor type, it is expected
 * to completely ignore the argument. i.e.: Passing a compressor that does not
 * require pixel dimensions null for the dimensions must not cause the
 * compressor to throw a NullPointerException.
 *
 * Classes implementing the Codec interface are expected to either
 * implement both compression methods or neither. (The same is expected for
 * decompression).
 *
 * @author Eric Kjellman egkjellman at wisc.edu
 */
public interface Codec {

  /**
   * Compresses data from an array into a ByteBuffer.
   * A preexisting destination ByteBuffer may be provided,
   * and a compressor may choose to use (or not) that ByteBuffer
   * if it has enough remaining space.
   * Regardless, the function returns a reference to the ByteBuffer
   * where the compressed data was actually stored.
   * This potentially avoids unnecessary buffer creation and copying,
   * since the ByteBuffer position indicates
   * how much of the buffer is actually used.
   *
   * @param target A preallocated ByteBuffer that may be used for the compressed data.  This may be <code>null</code> to force the compressor to allocate a fresh buffer.
   * @param data The data to be compressed.
   * @param options Options to be used during compression, if appropriate.
   * @return The ByteBuffer holding the compressed data.
   * @throws CodecException If input cannot be processed.
   */
  ByteBuffer compress(ByteBuffer target, byte[] data, CodecOptions options) throws CodecException;

  /**
   * Compresses a block of data.
   *
   * @param data The data to be compressed.
   * @param options Options to be used during compression, if appropriate.
   * @return The compressed data.
   * @throws CodecException If input is not a compressed data block of the
   *   appropriate type.
   */
  byte[] compress(byte[] data, CodecOptions options) throws CodecException;

  /**
   * Compresses a block of data.
   *
   * @param data The data to be compressed.
   * @param options Options to be used during compression, if appropriate.
   * @return The compressed data.
   * @throws CodecException If input is not a compressed data block of the
   *   appropriate type.
   */
  byte[] compress(byte[][] data, CodecOptions options) throws CodecException;

  /**
   * Decompresses a block of data.
   *
   * @param data the data to be decompressed
   * @param options Options to be used during decompression.
   * @return the decompressed data.
   * @throws CodecException If data is not valid.
   */
  byte[] decompress(byte[] data, CodecOptions options) throws CodecException;

  /**
   * Decompresses a block of data.
   *
   * @param data the data to be decompressed
   * @param options Options to be used during decompression.
   * @return the decompressed data.
   * @throws CodecException If data is not valid.
   */
  byte[] decompress(byte[][] data, CodecOptions options) throws CodecException;

  /**
   * Decompresses a block of data.
   *
   * @param data the data to be decompressed.
   * @return The decompressed data.
   * @throws CodecException If data is not valid compressed data for this
   *   decompressor.
   */
  byte[] decompress(byte[] data) throws CodecException;

  /**
   * Decompresses a block of data.
   *
   * @param data The data to be decompressed.
   * @return The decompressed data.
   * @throws CodecException If data is not valid compressed data for this
   *   decompressor.
   */
  byte[] decompress(byte[][] data) throws CodecException;

  /**
   * Decompresses data from the given RandomAccessInputStream.
   *
   * @param in The stream from which to read compressed data.
   * @param options Options to be used during decompression.
   * @return The decompressed data.
   * @throws CodecException If data is not valid compressed data for this
   *   decompressor.
   */
  byte[] decompress(RandomAccessInputStream in, CodecOptions options)
    throws CodecException, IOException;

}
