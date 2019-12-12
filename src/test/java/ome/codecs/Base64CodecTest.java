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

import java.util.Random;

import static org.testng.AssertJUnit.assertEquals;

import org.testng.annotations.Test;

public class Base64CodecTest {
  Base64Codec codec = new Base64Codec();

  @Test
  public void testRoundtripShortSequence() throws Exception {
    byte[] in = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
    roundtrip(in);
  }

  @Test
  public void testRoundtripCheckerboard() throws Exception {
    byte[] in = {0, (byte) 255, 0, (byte) 255,
                 (byte) 255, 0, (byte) 255, 0,
                 0, (byte) 255, 0, (byte) 255,
                 (byte) 255, 0, (byte) 255, 0};
    roundtrip(in);
  }

  @Test
  public void testCompressUncompressParity() throws Exception {
    for (int j = 0; j < 100; j++) {
      byte[] in = new byte[50000];
      new Random().nextBytes(in);
      roundtrip(in);
    }
  }

  private void roundtrip(byte[] in) throws CodecException {
    byte[] encoded = codec.compress(in, null);
    byte[] decoded = codec.decompress(encoded, null);
    assertEquals(in, decoded);
  }
}
