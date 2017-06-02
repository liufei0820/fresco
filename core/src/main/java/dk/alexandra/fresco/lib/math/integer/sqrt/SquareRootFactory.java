/*******************************************************************************
 * Copyright (c) 2015, 2016 FRESCO (http://github.com/aicis/fresco).
 *
 * This file is part of the FRESCO project.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 * FRESCO uses SCAPI - http://crypto.biu.ac.il/SCAPI, Crypto++, Miracl, NTL,
 * and Bouncy Castle. Please see these projects for any further licensing issues.
 *******************************************************************************/
package dk.alexandra.fresco.lib.math.integer.sqrt;

import dk.alexandra.fresco.framework.value.OInt;
import dk.alexandra.fresco.framework.value.SInt;

public interface SquareRootFactory {

	/**
	 * @param input
	 *            The input.
	 * @param maxInputLength
	 *            An upper bound for <i>log<sub>2</sub>(input)</i>.
	 * @param sqrt
	 *            An approximation of the square root of the input.
	 * 
	 * @return
	 */
	public SquareRootProtocol getSquareRootProtocol(SInt input, int maxInputLength, SInt sqrt);

	/**
	 * @param input
	 *            The input.
	 * @param maxInputLength
	 *            An upper bound for <i>log<sub>2</sub>(input)</i>.
	 * @param sqrt
	 *            An approximation of the square root of the input.
	 * @param precision
	 *            If this parameter is not null then the protocol will give it a
	 *            value which is a lower bound of the number of bits of
	 *            precision of the approximation.
	 * @return
	 */
	public SquareRootProtocol getSquareRootProtocol(SInt input, int maxInputLength, SInt sqrt, OInt precision);

}