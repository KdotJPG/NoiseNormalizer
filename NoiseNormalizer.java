/* 
 * Noise Normalizer
 *
 * Uses gradient ascent to attempt to determine the maximum value that a simplex-style coherent noise would generate,
 * so that you can go back and divide by it. And also, so you can re-test after you divide by it.
 * 
 * Configurable to different dimensionalities, kernel sizes, etc. I used this to generate the norms for SuperSimplex.
 * It might require some tweaking (which I may publish at some point) to provide the right lattice points for OpenSimplex.
 *
 * I also used this as practice with Java Streams, as you may notice. And I left a couple of commented code sections in,
 * in case they prove useful to you during troubleshooting.
 *
 * @author K.jpg
 */

import java.util.Arrays;
import java.util.stream.IntStream;

public class NoiseNormalizer {
	
	// Number of dimensions
	// FIRST_POINT and GRADIENTS should match
	// G (unskew factor) tends to differ as well
	public static int N = 2;
	
	// Tweak this and re-run to make sure you found the global maximum
	// Make sure you're inside the cluster of points
	public static double[] FIRST_POINT = { 0.2, 0.6 };
	
	// Unskew factor
	public static double G = -0.211324865405187;
	
	// Squared radius of kernel
	public static double RADIUS_SQ = 2.0 / 3.0;
	
	public static double[][] GRADIENTS = new double[][] {
		{ 0.0, 1.0 },
		{ 0.5, 0.8660254037844387 },
		{ 0.8660254037844387, 0.5 },
		{ 1.0, 0.0 },
		{ 0.8660254037844387, -0.5 },
		{ 0.5, -0.8660254037844387 },
		{ 0.0, -1.0 },
		{ -0.5, -0.8660254037844387 },
		{ -0.8660254037844387, -0.5 },
		{ -1.0, 0.0 },
		{ -0.8660254037844387, 0.5 },
		{ -0.5, 0.8660254037844387 }
	};

	public static void main(String[] args) {
		
		/*for (int j = 0; j < GRADIENTS.length; j++) {
			double[] currentGradient = GRADIENTS[j];
			for (int i = 0; i < N; i++) currentGradient[i] /= 0.05382168030817933;
		}*/
		
		// Surrounding lattice points (no repeats)
		// NOTE: May need to be changed to provide the right points for OpenSimplex
		int nLatticePoints = (2 << N) - 1;
		int[][] latticePointsCubespace = new int[nLatticePoints][];
		double[][] latticePoints = new double[nLatticePoints][];
		for (int k = 0; k < (1 << N); k++) {
			int kk = k;
			latticePointsCubespace[k] = IntStream.range(0, N).map(i -> ((kk >> i) & 1) - 1).toArray();
		}
		for (int k = 0; k < (1 << N) - 1; k++) {
			int kk = k;
			latticePointsCubespace[k + (1 << N)] = IntStream.range(0, N).map(i -> 1 - ((kk >> i) & 1)).toArray();
		}
		for (int k = 0; k < nLatticePoints; k++) {
			double skew = G * Arrays.stream(latticePointsCubespace[k]).sum();
			latticePoints[k] = Arrays.stream(latticePointsCubespace[k]).mapToDouble(v -> v + skew).toArray();
		}
		
		double[] movingCoord = FIRST_POINT;
		int[] latticePointGradientIndices = new int[nLatticePoints];
		double[] latticePointGradientDots = new double[nLatticePoints];
		double maxValue = 0;
		double rate = 1 / 32768d;
		while (true) {
			
			// Pick best gradients for current point
			for (int k = 0; k < nLatticePoints; k++) {
				double[] latticePoint = latticePoints[k];
				double currentBestDot = Double.NEGATIVE_INFINITY;
				int currentBestGradientIndex = -1;
				for (int j = 0; j < GRADIENTS.length; j++) {
					double[] currentGradient = GRADIENTS[j];
					double dot = IntStream.range(0, N).mapToDouble(i -> (movingCoord[i] - latticePoint[i]) * currentGradient[i]).sum();
					if (dot > currentBestDot) {
						currentBestGradientIndex = j;
						currentBestDot = dot;
					}
				}
				latticePointGradientIndices[k] = currentBestGradientIndex;
				latticePointGradientDots[k] = currentBestDot;
			}
			
			// Get value and gradient direction at current point
			double currentValue = 0;
			double[] currentDerivative = new double[N];
			for (int k = 0; k < nLatticePoints; k++) {
				double[] latticePoint = latticePoints[k];
				double[] gradient = GRADIENTS[latticePointGradientIndices[k]];
				double dot = latticePointGradientDots[k];
				
				double attn = RADIUS_SQ - IntStream.range(0, N).mapToDouble(i -> (movingCoord[i] - latticePoint[i])).map(v -> v * v).sum();
				if (attn > 0) {
					double attnSq = attn * attn;
					double thisValue = attnSq * attnSq * dot;
					currentValue += thisValue;
					
					double dAttnMultiplier = -8 * attnSq * attn;
					double[] thisDerivative = IntStream.range(0, N).mapToDouble(i -> dAttnMultiplier * (movingCoord[i] - latticePoint[i]) * dot + attnSq * attnSq * gradient[i]).toArray();
					for (int l = 0; l < N; l++) currentDerivative[l] += thisDerivative[l];
				}
				
			}
			if (currentValue > maxValue) maxValue = currentValue;
			
			double curentDerivativeMagnitudeSq = Arrays.stream(currentDerivative).map(v -> v * v).sum();
			/*if (previousValue > currentValue) {
				rate /= (1+1/64d);
			}*/
			if (curentDerivativeMagnitudeSq > 0) {// 1/65536d/65536d/65536d/65536d/65536d/65536d) {
				double rateToTry = rate;
				double rateToStopAt = rate * 128;
				boolean changedWithoutPrecisionLossMakingItZero = false;
				while (true) {
					for (int l = 0; l < N; l++) {
						double newValue = movingCoord[l] + currentDerivative[l] * rateToTry;
						if (newValue != movingCoord[l]) changedWithoutPrecisionLossMakingItZero = true;
						movingCoord[l] = newValue;
					}
					System.out.println("Moving by: " + curentDerivativeMagnitudeSq + ", current value: " + currentValue);
					if (changedWithoutPrecisionLossMakingItZero) break; // So we can continue
					if (rate >= rateToStopAt) {
						System.out.println("Change not big enough to not lose all precision, or something.");
						break;
					}
					rate *= 2;
				}
				if (changedWithoutPrecisionLossMakingItZero) continue;
			}
			System.out.println("Max value found: " + maxValue);
			System.out.println("Near location " + Arrays.toString(movingCoord));
			System.out.println("Which had gradient indices " + Arrays.toString(latticePointGradientIndices));
			System.out.println("And derivative " + Arrays.toString(currentDerivative));
			break;
			
		}
	}
}