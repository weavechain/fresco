/*******************************************************************************
 * Copyright (c) 2015 FRESCO (http://github.com/aicis/fresco).
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

import dk.alexandra.fresco.framework.ProtocolProducer;
import dk.alexandra.fresco.framework.value.SInt;
import dk.alexandra.fresco.lib.field.integer.BasicNumericFactory;
import dk.alexandra.fresco.lib.helper.AbstractSimpleProtocol;
import dk.alexandra.fresco.lib.helper.sequential.SequentialProtocolProducer;
import dk.alexandra.fresco.lib.math.integer.binary.RightShiftFactory;
import dk.alexandra.fresco.lib.math.integer.division.DivisionFactory;

/**
 * This class implements a protocol for approximating the square root of a
 * secret shared integer using the <a href=
 * "https://en.wikipedia.org/wiki/Methods_of_computing_square_roots#Babylonian_method"
 * >Babylonian Method</a>.
 * 
 * @author Jonas Lindstrøm (jonas.lindstrom@alexandra.dk)
 *
 */
public class SquareRootProtocolImpl extends AbstractSimpleProtocol implements SquareRootProtocol {

	// Input
	private SInt input;
	private int maxInputLength;
	private SInt result;

	// Factories
	private final BasicNumericFactory basicNumericFactory;
	private final DivisionFactory divisionFactory;
	private final RightShiftFactory rightShiftFactory;

	public SquareRootProtocolImpl(SInt input, int maxInputLength, SInt result,
			BasicNumericFactory basicNumericFactory, DivisionFactory divisionFactory,
			RightShiftFactory rightShiftFactory) {
		this.input = input;
		this.maxInputLength = maxInputLength;

		this.result = result;

		this.basicNumericFactory = basicNumericFactory;
		this.divisionFactory = divisionFactory;
		this.rightShiftFactory = rightShiftFactory;
	}

	@Override
	protected ProtocolProducer initializeGateProducer() {
		SequentialProtocolProducer squareRootProtocol = new SequentialProtocolProducer();

		/*
		 * First guess is x << maxInputLength / 2
		 */
		SInt y = basicNumericFactory.getSInt();
		squareRootProtocol.append(rightShiftFactory.getRepeatedRightShiftProtocol(input,
				maxInputLength / 2, y));

		/*
		 * How much precision can we use for the division while still keep the
		 * limit? See JavaDoc for getDivisionProtocol for details.
		 */
		int precision = log2((basicNumericFactory.getMaxBitLength() - maxInputLength)
				/ log2(maxInputLength / 2));

		/*
		 * We iterate y[n+1] = (y[n] + x / y[n]) / 2.
		 * 
		 * Convergence is quadratic (the number of correct digits rougly doubles
		 * on each iteration) so assuming we have at least one digit correct
		 * after first iteration, we need at most log2(maxInputLength)
		 * iterations in total.
		 */
		int iterations = log2(maxInputLength);
		for (int i = 1; i < iterations; i++) {
			SInt quotient = basicNumericFactory.getSInt();
			squareRootProtocol.append(divisionFactory.getDivisionProtocol(input, y,
					maxInputLength / 2, precision, quotient));

			SInt sum = basicNumericFactory.getSInt();
			squareRootProtocol.append(basicNumericFactory.getAddProtocol(y, quotient, sum));

			if (i < iterations - 1) {
				y = basicNumericFactory.getSInt();
				squareRootProtocol.append(rightShiftFactory.getRightShiftProtocol(sum, y));
			} else {
				squareRootProtocol.append(rightShiftFactory.getRightShiftProtocol(sum, result));
			}
		}
		return squareRootProtocol;
	}

	/**
	 * Calculate the base-2 logarithm of <i>x</i>, <i>log<sub>2</sub>(x)</i>.
	 * 
	 * @param x
	 * @return
	 */
	private static int log2(int x) {
		return (int) (Math.log(x) / Math.log(2));
	}

}
