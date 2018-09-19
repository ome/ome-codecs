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

import java.nio.charset.Charset;
import java.util.Random;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertTrue;

import org.testng.annotations.Test;

public class LZWCodecTest {
  LZWCodec codec = new LZWCodec();

  @Test
  public void testCompressShortUniqueSequence() throws Exception {
    byte[] in = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 };
    byte[] expected = { -128, 0, 0, 32, 32, 24, 16, 10, 6, 3, -126, 1, 32, -88, 8 };
    byte[] comp = codec.compress(in, null);
    assertEquals(expected, comp);
  }

  @Test
  public void testCompressShortNonUniqueSequence() throws Exception {
    byte[] in = "This is the first day of the rest of your life".getBytes(Charset.forName("UTF-8"));
    byte[] expected = {
      -128, 21, 13, 6, -109, -104, -126, 8, 32, 58, 26, 12, -94, 3, 49, -92, -28, 115, 58, 8, 12,
      -122, 19, -56, -128, -34, 102, -124, 66, -124, 7, 35, 44, 66, 45, 24, 60, -101, -50, -89, 33,
      1, -80, -46, 102, 50, -64, 64
    };
    byte[] comp = codec.compress(in, null);
    assertEquals(expected, comp);
  }

  @Test
  public void speedCompare() throws Exception {
    byte[] in = new byte[50000];
    new Random().nextBytes(in);
    LZWCodecOld codecOld = new LZWCodecOld();

    long start = System.currentTimeMillis();
    for (int j = 0; j < 10000; j++) {
      byte[] comp = codecOld.compress(in, null);
    }
    long oldTime = System.currentTimeMillis() - start;

    start = System.currentTimeMillis();
    for (int j = 0; j < 10000; j++) {
      byte[] comp = codec.compress(in, null);
    }
    long newTime = System.currentTimeMillis() - start;

    System.out.println("Old time to compress: " + oldTime);
    System.out.println("New time to compress: " + newTime);
    System.out.println("Improvement: " + ((oldTime - newTime) * 100 / oldTime) + "%");
    assertTrue(newTime <= oldTime);
  }

  @Test
  public void testCompressUncompressParity() throws Exception {
    for (int j = 0; j < 100; j++) {
      byte[] in = new byte[50000];
      new Random().nextBytes(in);
      byte[] comp = codec.compress(in, null);
      CodecOptions opt = new CodecOptions();
      opt.maxBytes = in.length;
      byte[] out = codec.decompress(comp, opt);
      assertEquals(in, out);
    }
  }
}
