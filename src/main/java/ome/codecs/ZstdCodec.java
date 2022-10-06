/*
 * #%L
 * Image encoding and decoding routines.
 * %%
 * Copyright (C) 2005 - 2022 Open Microscopy Environment:
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

import loci.common.RandomAccessInputStream;
import java.io.EOFException;
import java.io.IOException;
import io.airlift.compress.zstd.ZstdDecompressor;

/**
 * This class implements Zstandard decompression.
 *
 * @author Wim Pomp w.pomp at nki.nl
 */
public class ZstdCodec extends BaseCodec {

  @Override
  public byte[] compress(byte[] data, CodecOptions options)
    throws CodecException
  {
    if (data == null || data.length == 0)
      throw new IllegalArgumentException("No data to compress");
    // TODO: Add compression support.
    throw new UnsupportedCompressionException("Zstandard Compression not currently supported.");
  }

  @Override
  public byte[] decompress(RandomAccessInputStream in, CodecOptions options)
    throws CodecException, IOException
  {
    ByteVector bytes = new ByteVector();
    byte[] buf = new byte[8192];
    int r;
    // read until eof reached
    try {
      while ((r = in.read(buf, 0, buf.length)) > 0) bytes.add(buf, 0, r);
    }
    catch (EOFException ignored) { }

    byte[] data = bytes.toByteArray();
    return decompress(data);
}
  
  @Override
  public byte[] decompress(byte[] data)
    throws CodecException
  {
    return decompress(data, 0, data.length);
  }
  
  public byte[] decompress(byte[] data, int inputOffset, int length)
    throws CodecException
  {
    ZstdDecompressor decompressor = new ZstdDecompressor();
    byte[] output = new byte[(int) ZstdDecompressor.getDecompressedSize(data, inputOffset, length)];
    decompressor.decompress(data, inputOffset, length, output, 0, output.length);
    return output;
  }
}