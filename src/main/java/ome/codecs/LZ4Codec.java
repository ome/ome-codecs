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
import java.io.IOException;
import java.util.Arrays;

import io.airlift.compress.MalformedInputException;
import io.airlift.compress.lz4.Lz4Decompressor;
import io.airlift.compress.lz4.Lz4Compressor;


/**
 * This class implements LZ4 decompression.
 *
 * @author Rolf Harkes r.harkes at nki.nl
 */
public class LZ4Codec extends BaseCodec {

    /* @see BaseCodec#compress(byte[], CodecOptions) */
    @Override
    public byte[] compress(byte[] data, CodecOptions options)
            throws CodecException
    {
        if (data == null || data.length == 0)
            throw new IllegalArgumentException("No data to compress");
        Lz4Compressor compressor = new Lz4Compressor();
        byte[] output = new byte[compressor.maxCompressedLength(data.length)];
        int len = compressor.compress(data,0, data.length, output, 0, output.length);
        return Arrays.copyOfRange(output, 0, len);
    }

    /* @see BaseCodec#decompress(RandomAccessInputStream, CodecOptions) */
    @Override
    public byte[] decompress(RandomAccessInputStream in, CodecOptions options)
            throws CodecException, IOException
    {
        long byteCount = in.length() - in.getFilePointer();
        if (byteCount > Integer.MAX_VALUE || byteCount < 0) {
            throw new CodecException("Integer overflow detected when calculating file byteCount.");
        }
        byte[] data = new byte[(int) byteCount];
        in.readFully(data);
        return decompress(data, options);
    }

    /* @see BaseCodec#decompress(byte[]) */
    @Override
    public byte[] decompress(byte[] data)
            throws CodecException
    {
        return decompress(data, 0, data.length, data.length*255);
    }

    /* @see BaseCodec#decompress(byte[], CodecOptions) */
    @Override
    public byte[] decompress(byte[] data, CodecOptions options)
            throws CodecException
    {
        return decompress(data, 0, data.length, options.maxBytes);
    }

    /**
     * Decompresses a block of data of specified length from an initial offset.
     *
     * @param data The data to be decompressed.
     * @param inputOffset The position of the input data at which to begin decompression.
     * @param length The length of input data to be decompressed.
     * @return The decompressed data.
     * @throws CodecException If data is not valid.
     */
    public byte[] decompress(byte[] data, int inputOffset, int length, int outputLength)
            throws CodecException
    {
        Lz4Decompressor decompressor = new Lz4Decompressor();
        byte[] output = new byte[outputLength];
        try {
            decompressor.decompress(data, inputOffset, length, output, 0, outputLength);
        }
        catch(MalformedInputException e) {
            throw new CodecException(e);
        }
        return output;
    }
}
