 /* 
 * Noise Normalizer, for Perlin.
 *
 * Note that Perlin noise generally produces poor directional variety, and consists of mostly axis-aligned features.
 * Perlin is was the original gradient noise algorithm, but there have been many advances which address or fix its shortcomings.
 * "Improved Perlin Noise" (2002) does not fix directional bias, just value clumping and derivative continuity.
 * Aside from instead using Simplex-type noise, Perlin can be domain-rotated in 3D to produce good-looking 2D slices,
 * and the 4D noise can be domain-rotated to produce good 3D slices.
 * The domain-rotated 3D can also be used where the 3rd coordinate is only needed for animation or enabling terrain overhangs.
 * This is implemented here: https://github.com/KdotJPG/Noise-Extras/blob/master/DomainRotatedClassicNoise.java
 * as well as in FastNoiseLite: https://github.com/Auburn/FastNoiseLite (setRotationType3D - ImproveXYPlanes, ImproveXZPlanes)
 *
 * This tool uses gradient ascent to attempt to determine the maximum value that Perlin noise would generate,
 * so that you can go back and divide by it. And also, so you can re-test after you divide by it.
 *
 * I also used this as practice for Java Streams. Many of the components are implemented using them, though not all are.
 *
 * @author K.jpg
 */

import java.util.Arrays;
import java.util.stream.IntStream;
import java.util.Random;

public class NoiseNormalizerP {
    
    private enum FadeCurveType {
        Quintic, Cubic, None
    }
    
    private static double CONVERGENCE_RATE = 1.0 / 131072;
    private static int MAX_RATE_RETRY_MULTIPLIER_EXPONENT = 7;
    
    // Use this to test your normalization constant, to make sure the resulting noise does not exceed your target range.
    // Leave it at 1 to compute the unmodified noise bounds. Be sure to set this back to 1 when you're not using it.
    private static double GRADIENT_MULTIPLIER = 1;
    
    private static int N_DIMENSIONS = 3;
    private static FadeCurveType FADE_CURVE_TYPE = FadeCurveType.Quintic;
    private static double[][] GRADIENTS = new double[][] {
        { 1,  1,  0}, 
        { 1, -1,  0},
        {-1,  1,  0},
        {-1, -1,  0},
        { 1,  0,  1},
        { 1,  0, -1},
        {-1,  0,  1},
        {-1,  0, -1},
        { 0,  1,  1},
        { 0,  1, -1},
        { 0, -1,  1},
        { 0, -1, -1}
    };
    
    /*
    private static int N_DIMENSIONS = 4;
    private static FadeCurveType FADE_CURVE_TYPE = FadeCurveType.Quintic;
    private static double[][] GRADIENTS = new double[][] {
        { 1,  1,  1,  0},    
        {-1,  1,  1,  0},    
        { 1, -1,  1,  0},    
        {-1, -1,  1,  0},    
        { 1,  1, -1,  0},    
        {-1,  1, -1,  0},    
        { 1, -1, -1,  0},    
        {-1, -1, -1,  0},    
        { 1,  1,  0,  1},    
        {-1,  1,  0,  1},    
        { 1, -1,  0,  1},    
        {-1, -1,  0,  1},    
        { 1,  1,  0, -1},    
        {-1,  1,  0, -1},    
        { 1, -1,  0, -1},    
        {-1, -1,  0, -1},    
        { 1,  0,  1,  1},    
        {-1,  0,  1,  1},    
        { 1,  0, -1,  1},    
        {-1,  0, -1,  1},    
        { 1,  0,  1, -1},    
        {-1,  0,  1, -1},    
        { 1,  0, -1, -1},    
        {-1,  0, -1, -1},    
        { 0,  1,  1,  1},    
        { 0, -1,  1,  1},    
        { 0,  1, -1,  1},    
        { 0, -1, -1,  1},    
        { 0,  1,  1, -1},    
        { 0, -1,  1, -1},    
        { 0,  1, -1, -1},    
        { 0, -1, -1, -1}
    };*/
    
    /*
    private static int N_DIMENSIONS = 2;
    private static FadeCurveType FADE_CURVE_TYPE = FadeCurveType.Quintic;
    private static double[][] GRADIENTS = new double[][] {
        { 1,  1},
        { 1, -1},
        {-1,  1},
        {-1, -1}
    };*/
    
    /*
    // SpongePowered noise vectors
    private static int N_DIMENSIONS = 3;
    private static FadeCurveType FADE_CURVE_TYPE = FadeCurveType.Quintic;
    private static double[][] GRADIENTS = new double[][] {
        { -0.763874, -0.596439, -0.246489 }, { 0.396055, 0.904518, -0.158073 }, { -0.499004, -0.8665, -0.0131631 }, { 0.468724, -0.824756, 0.316346 },
        { 0.829598, 0.43195, 0.353816 }, { -0.454473, 0.629497, -0.630228 }, { -0.162349, -0.869962, -0.465628 }, { 0.932805, 0.253451, 0.256198 },
        { -0.345419, 0.927299, -0.144227 }, { -0.715026, -0.293698, -0.634413 }, { -0.245997, 0.717467, -0.651711 }, { -0.967409, -0.250435, -0.037451 },
        { 0.901729, 0.397108, -0.170852 }, { 0.892657, -0.0720622, -0.444938 }, { 0.0260084, -0.0361701, 0.999007 }, { 0.949107, -0.19486, 0.247439 },
        { 0.471803, -0.807064, -0.355036 }, { 0.879737, 0.141845, 0.453809 }, { 0.570747, 0.696415, 0.435033 }, { -0.141751, -0.988233, -0.0574584 },
        { -0.58219, -0.0303005, 0.812488 }, { -0.60922, 0.239482, -0.755975 }, { 0.299394, -0.197066, -0.933557 }, { -0.851615, -0.220702, -0.47544 },
        { 0.848886, 0.341829, -0.403169 }, { -0.156129, -0.687241, 0.709453 }, { -0.665651, 0.626724, 0.405124 }, { 0.595914, -0.674582, 0.43569 },
        { 0.171025, -0.509292, 0.843428 }, { 0.78605, 0.536414, -0.307222 }, { 0.18905, -0.791613, 0.581042 }, { -0.294916, 0.844994, 0.446105 },
        { 0.342031, -0.58736, -0.7335 }, { 0.57155, 0.7869, 0.232635 }, { 0.885026, -0.408223, 0.223791 }, { -0.789518, 0.571645, 0.223347 },
        { 0.774571, 0.31566, 0.548087 }, { -0.79695, -0.0433603, -0.602487 }, { -0.142425, -0.473249, -0.869339 }, { -0.0698838, 0.170442, 0.982886 },
        { 0.687815, -0.484748, 0.540306 }, { 0.543703, -0.534446, -0.647112 }, { 0.97186, 0.184391, -0.146588 }, { 0.707084, 0.485713, -0.513921 },
        { 0.942302, 0.331945, 0.043348 }, { 0.499084, 0.599922, 0.625307 }, { -0.289203, 0.211107, 0.9337 }, { 0.412433, -0.71667, -0.56239 },
        { 0.87721, -0.082816, 0.47291 }, { -0.420685, -0.214278, 0.881538 }, { 0.752558, -0.0391579, 0.657361 }, { 0.0765725, -0.996789, 0.0234082 },
        { -0.544312, -0.309435, -0.779727 }, { -0.455358, -0.415572, 0.787368 }, { -0.874586, 0.483746, 0.0330131 }, { 0.245172, -0.0838623, 0.965846 },
        { 0.382293, -0.432813, 0.81641 }, { -0.287735, -0.905514, 0.311853 }, { -0.667704, 0.704955, -0.239186 }, { 0.717885, -0.464002, -0.518983 },
        { 0.976342, -0.214895, 0.0240053 }, { -0.0733096, -0.921136, 0.382276 }, { -0.986284, 0.151224, -0.0661379 }, { -0.899319, -0.429671, 0.0812908 },
        { 0.652102, -0.724625, 0.222893 }, { 0.203761, 0.458023, -0.865272 }, { -0.030396, 0.698724, -0.714745 }, { -0.460232, 0.839138, 0.289887 },
        { -0.0898602, 0.837894, 0.538386 }, { -0.731595, 0.0793784, 0.677102 }, { -0.447236, -0.788397, 0.422386 }, { 0.186481, 0.645855, -0.740335 },
        { -0.259006, 0.935463, 0.240467 }, { 0.445839, 0.819655, -0.359712 }, { 0.349962, 0.755022, -0.554499 }, { -0.997078, -0.0359577, 0.0673977 },
        { -0.431163, -0.147516, -0.890133 }, { 0.299648, -0.63914, 0.708316 }, { 0.397043, 0.566526, -0.722084 }, { -0.502489, 0.438308, -0.745246 },
        { 0.0687235, 0.354097, 0.93268 }, { -0.0476651, -0.462597, 0.885286 }, { -0.221934, 0.900739, -0.373383 }, { -0.956107, -0.225676, 0.186893 },
        { -0.187627, 0.391487, -0.900852 }, { -0.224209, -0.315405, 0.92209 }, { -0.730807, -0.537068, 0.421283 }, { -0.0353135, -0.816748, 0.575913 },
        { -0.941391, 0.176991, -0.287153 }, { -0.154174, 0.390458, 0.90762 }, { -0.283847, 0.533842, 0.796519 }, { -0.482737, -0.850448, 0.209052 },
        { -0.649175, 0.477748, 0.591886 }, { 0.885373, -0.405387, -0.227543 }, { -0.147261, 0.181623, -0.972279 }, { 0.0959236, -0.115847, -0.988624 },
        { -0.89724, -0.191348, 0.397928 }, { 0.903553, -0.428461, -0.00350461 }, { 0.849072, -0.295807, -0.437693 }, { 0.65551, 0.741754, -0.141804 },
        { 0.61598, -0.178669, 0.767232 }, { 0.0112967, 0.932256, -0.361623 }, { -0.793031, 0.258012, 0.551845 }, { 0.421933, 0.454311, 0.784585 },
        { -0.319993, 0.0401618, -0.946568 }, { -0.81571, 0.551307, -0.175151 }, { -0.377644, 0.00322313, 0.925945 }, { 0.129759, -0.666581, -0.734052 },
        { 0.601901, -0.654237, -0.457919 }, { -0.927463, -0.0343576, -0.372334 }, { -0.438663, -0.868301, -0.231578 }, { -0.648845, -0.749138, -0.133387 },
        { 0.507393, -0.588294, 0.629653 }, { 0.726958, 0.623665, 0.287358 }, { 0.411159, 0.367614, -0.834151 }, { 0.806333, 0.585117, -0.0864016 },
        { 0.263935, -0.880876, 0.392932 }, { 0.421546, -0.201336, 0.884174 }, { -0.683198, -0.569557, -0.456996 }, { -0.117116, -0.0406654, -0.992285 },
        { -0.643679, -0.109196, -0.757465 }, { -0.561559, -0.62989, 0.536554 }, { 0.0628422, 0.104677, -0.992519 }, { 0.480759, -0.2867, -0.828658 },
        { -0.228559, -0.228965, -0.946222 }, { -0.10194, -0.65706, -0.746914 }, { 0.0689193, -0.678236, 0.731605 }, { 0.401019, -0.754026, 0.52022 },
        { -0.742141, 0.547083, -0.387203 }, { -0.00210603, -0.796417, -0.604745 }, { 0.296725, -0.409909, -0.862513 }, { -0.260932, -0.798201, 0.542945 },
        { -0.641628, 0.742379, 0.192838 }, { -0.186009, -0.101514, 0.97729 }, { 0.106711, -0.962067, 0.251079 }, { -0.743499, 0.30988, -0.592607 },
        { -0.795853, -0.605066, -0.0226607 }, { -0.828661, -0.419471, -0.370628 }, { 0.0847218, -0.489815, -0.8677 }, { -0.381405, 0.788019, -0.483276 },
        { 0.282042, -0.953394, 0.107205 }, { 0.530774, 0.847413, 0.0130696 }, { 0.0515397, 0.922524, 0.382484 }, { -0.631467, -0.709046, 0.313852 },
        { 0.688248, 0.517273, 0.508668 }, { 0.646689, -0.333782, -0.685845 }, { -0.932528, -0.247532, -0.262906 }, { 0.630609, 0.68757, -0.359973 },
        { 0.577805, -0.394189, 0.714673 }, { -0.887833, -0.437301, -0.14325 }, { 0.690982, 0.174003, 0.701617 }, { -0.866701, 0.0118182, 0.498689 },
        { -0.482876, 0.727143, 0.487949 }, { -0.577567, 0.682593, -0.447752 }, { 0.373768, 0.0982991, 0.922299 }, { 0.170744, 0.964243, -0.202687 },
        { 0.993654, -0.035791, -0.106632 }, { 0.587065, 0.4143, -0.695493 }, { -0.396509, 0.26509, -0.878924 }, { -0.0866853, 0.83553, -0.542563 },
        { 0.923193, 0.133398, -0.360443 }, { 0.00379108, -0.258618, 0.965972 }, { 0.239144, 0.245154, -0.939526 }, { 0.758731, -0.555871, 0.33961 },
        { 0.295355, 0.309513, 0.903862 }, { 0.0531222, -0.91003, -0.411124 }, { 0.270452, 0.0229439, -0.96246 }, { 0.563634, 0.0324352, 0.825387 },
        { 0.156326, 0.147392, 0.976646 }, { -0.0410141, 0.981824, 0.185309 }, { -0.385562, -0.576343, -0.720535 }, { 0.388281, 0.904441, 0.176702 },
        { 0.945561, -0.192859, -0.262146 }, { 0.844504, 0.520193, 0.127325 }, { 0.0330893, 0.999121, -0.0257505 }, { -0.592616, -0.482475, -0.644999 },
        { 0.539471, 0.631024, -0.557476 }, { 0.655851, -0.027319, -0.754396 }, { 0.274465, 0.887659, 0.369772 }, { -0.123419, 0.975177, -0.183842 },
        { -0.223429, 0.708045, 0.66989 }, { -0.908654, 0.196302, 0.368528 }, { -0.95759, -0.00863708, 0.288005 }, { 0.960535, 0.030592, 0.276472 },
        { -0.413146, 0.907537, 0.0754161 }, { -0.847992, 0.350849, -0.397259 }, { 0.614736, 0.395841, 0.68221 }, { -0.503504, -0.666128, -0.550234 },
        { -0.268833, -0.738524, -0.618314 }, { 0.792737, -0.60001, -0.107502 }, { -0.637582, 0.508144, -0.579032 }, { 0.750105, 0.282165, -0.598101 },
        { -0.351199, -0.392294, -0.850155 }, { 0.250126, -0.960993, -0.118025 }, { -0.732341, 0.680909, -0.0063274 }, { -0.760674, -0.141009, 0.633634 },
        { 0.222823, -0.304012, 0.926243 }, { 0.209178, 0.505671, 0.836984 }, { 0.757914, -0.56629, -0.323857 }, { -0.782926, -0.339196, 0.52151 },
        { -0.462952, 0.585565, 0.665424 }, { 0.61879, 0.194119, -0.761194 }, { 0.741388, -0.276743, 0.611357 }, { 0.707571, 0.702621, 0.0752872 },
        { 0.156562, 0.819977, 0.550569 }, { -0.793606, 0.440216, 0.42 }, { 0.234547, 0.885309, -0.401517 }, { 0.132598, 0.80115, -0.58359 },
        { -0.377899, -0.639179, 0.669808 }, { -0.865993, -0.396465, 0.304748 }, { -0.624815, -0.44283, 0.643046 }, { -0.485705, 0.825614, -0.287146 },
        { -0.971788, 0.175535, 0.157529 }, { -0.456027, 0.392629, 0.798675 }, { -0.0104443, 0.521623, -0.853112 }, { -0.660575, -0.74519, 0.091282 },
        { -0.0157698, -0.307475, -0.951425 }, { -0.603467, -0.250192, 0.757121 }, { 0.506876, 0.25006, 0.824952 }, { 0.255404, 0.966794, 0.00884498 },
        { 0.466764, -0.874228, -0.133625 }, { 0.475077, -0.0682351, -0.877295 }, { -0.224967, -0.938972, -0.260233 }, { -0.377929, -0.814757, -0.439705 },
        { -0.305847, 0.542333, -0.782517 }, { 0.26658, -0.902905, -0.337191 }, { 0.0275773, 0.322158, -0.946284 }, { 0.0185422, 0.716349, 0.697496 },
        { -0.20483, 0.978416, 0.0273371 }, { -0.898276, 0.373969, 0.230752 }, { -0.00909378, 0.546594, 0.837349 }, { 0.6602, -0.751089, 0.000959236 },
        { 0.855301, -0.303056, 0.420259 }, { 0.797138, 0.0623013, -0.600574 }, { 0.48947, -0.866813, 0.0951509 }, { 0.251142, 0.674531, 0.694216 },
        { -0.578422, -0.737373, -0.348867 }, { -0.254689, -0.514807, 0.818601 }, { 0.374972, 0.761612, 0.528529 }, { 0.640303, -0.734271, -0.225517 },
        { -0.638076, 0.285527, 0.715075 }, { 0.772956, -0.15984, -0.613995 }, { 0.798217, -0.590628, 0.118356 }, { -0.986276, -0.0578337, -0.154644 },
        { -0.312988, -0.94549, 0.0899272 }, { -0.497338, 0.178325, 0.849032 }, { -0.101136, -0.981014, 0.165477 }, { -0.521688, 0.0553434, -0.851339 },
        { -0.786182, -0.583814, 0.202678 }, { -0.565191, 0.821858, -0.0714658 }, { 0.437895, 0.152598, -0.885981 }, { -0.92394, 0.353436, -0.14635 },
        { 0.212189, -0.815162, -0.538969 }, { -0.859262, 0.143405, -0.491024 }, { 0.991353, 0.112814, 0.0670273 }, { 0.0337884, -0.979891, -0.196654 }
    };*/
    
    static {
        for (int i = 0; i < GRADIENTS.length; i++) {
            for (int j = 0; j < GRADIENTS[i].length; j++) GRADIENTS[i][j] *= GRADIENT_MULTIPLIER;
        }
    }

    public static double fadeCurve(double a) {
        if (a < 0) return fadeCurve(-a);
        if (a >= 1) return 0;
        a = 1 - a;
        
        switch (FADE_CURVE_TYPE) {
            default:
            case Quintic:            
                double a3 = a * a * a;
                double a4 = a3 * a;
                double a5 = a4 * a;
                return (6.0 * a5) - (15.0 * a4) + (10.0 * a3);
            case Cubic:
                return (a * a * (3.0 - 2.0 * a));
            case None:
                return a;
        }
    }

    public static double dFadeCurve(double a) {
        if (a < 0) return -dFadeCurve(-a);
        if (a >= 1) return 0;
        a = 1 - a;
        
        switch (FADE_CURVE_TYPE) {
            default:
            case Quintic:
                double a2 = a * a;
                double a3 = a2 * a;
                double a4 = a3 * a;
                return -( (30.0 * a4) - (60.0 * a3) + (30.0 * a2) );
            case Cubic:
                return -a * (6.0 - 6.0 * a);
            case None:
                return -1;
        }
    }
    
    private static int MAX_RATE_RETRY_MULTIPLIER = 1 << MAX_RATE_RETRY_MULTIPLIER_EXPONENT;
    private static int N_LATTICE_VERTICES = 1 << N_DIMENSIONS;
    public static void main(String[] args) {
        
        // Surrounding vertices on lattice, range (0, 0, ..., 0) to (1, 1, ..., 1)
        int[][] latticePoints = new int[N_LATTICE_VERTICES][];
        for (int k = 0; k < N_LATTICE_VERTICES; k++) {
            int kk = k;
            latticePoints[k] = IntStream.range(0, N_DIMENSIONS).map(i -> ((kk >> i) & 1)).toArray();
        }
        
        Random random = new Random();
        double[] currentDerivativeVector = new double[N_DIMENSIONS];
        int[] latticePointGradientIndices = new int[N_LATTICE_VERTICES];
        double[] latticePointGradientDots = new double[N_LATTICE_VERTICES];
        double maxValue = 0;
        double[] startingCoord = new double[N_DIMENSIONS];
        double[] movingCoord = new double[N_DIMENSIONS];
        double[] maximizingStartingCoord = new double[N_DIMENSIONS];
        double[] maximizingCoord = new double[N_DIMENSIONS];
        double[] maximizingDerivativeVector = new double[N_DIMENSIONS];
        int[] maximizingGradientIndices = new int[N_LATTICE_VERTICES];
        
        while (true) {
            for (int i = 0; i < N_DIMENSIONS; i++) startingCoord[i] = movingCoord[i] = random.nextDouble();
            int lastExponent = Integer.MAX_VALUE;
            
            // Gradient Ascent Loop
            while (true) {
                
                // Pick best gradients for current point
                for (int k = 0; k < N_LATTICE_VERTICES; k++) {
                    int[] latticePoint = latticePoints[k];
                    double currentBestDot = Double.NEGATIVE_INFINITY;
                    int currentBestGradientIndex = -1;
                    for (int j = 0; j < GRADIENTS.length; j++) {
                        double[] currentGradient = GRADIENTS[j];
                        double dot = IntStream.range(0, N_DIMENSIONS).mapToDouble(i -> (movingCoord[i] - latticePoint[i]) * currentGradient[i]).sum();
                        if (dot > currentBestDot) {
                            currentBestGradientIndex = j;
                            currentBestDot = dot;
                        }
                    }
                    latticePointGradientIndices[k] = currentBestGradientIndex;
                    latticePointGradientDots[k] = currentBestDot;
                }
                
                // Get noise value and gradient (derivative vector) at current point
                double currentValue = 0;
                for (int i = 0; i < N_DIMENSIONS; i++) currentDerivativeVector[i] = 0;
                for (int k = 0; k < N_LATTICE_VERTICES; k++) {
                    int[] latticePoint = latticePoints[k];
                    double[] gradient = GRADIENTS[latticePointGradientIndices[k]];
                    double dot = latticePointGradientDots[k];
                    
                    // Cube-shaped fade-curve falloff function derived from interpolation
                    double falloff = IntStream.range(0, N_DIMENSIONS).mapToDouble(i -> (movingCoord[i] - latticePoint[i])).map(NoiseNormalizerP::fadeCurve).reduce(1, (a, b) -> a*b);
                    
                    // Derivative vector of this vertex gradient contribution
                    double[] dFalloff = new double[N_DIMENSIONS];
                    for (int l = 0; l < N_DIMENSIONS; l++) {
                        dFalloff[l] = 1.0;
                        for (int i = 0; i < N_DIMENSIONS; i++) {
                            double a = movingCoord[i] - latticePoint[i];
                            dFalloff[l] *= (i == l) ? dFadeCurve(a) : fadeCurve(a);
                        }
                    }
                    for (int l = 0; l < N_DIMENSIONS; l++) {
                        currentDerivativeVector[l] += gradient[l] * falloff + dot * dFalloff[l];
                    }
                    
                    // Add value
                    currentValue += falloff * dot;
                }
                if (currentValue > maxValue) {
                    maxValue = currentValue;
                    for (int i = 0; i < N_DIMENSIONS; i++) maximizingStartingCoord[i] = startingCoord[i];
                    for (int i = 0; i < N_DIMENSIONS; i++) maximizingCoord[i] = movingCoord[i];
                    for (int i = 0; i < N_DIMENSIONS; i++) maximizingDerivativeVector[i] = currentDerivativeVector[i];
                    for (int i = 0; i < N_LATTICE_VERTICES; i++) maximizingGradientIndices[i] = latticePointGradientIndices[i];
                }
                
                // Perform the actual point movement
                double currentDerivativeMagnitudeSq = Arrays.stream(currentDerivativeVector).map(v -> v * v).sum();
                    
                // To give the ascent a good chance to proceed, we try doubling the rate a couple times if it didn't change the coordinate the first time.
                double rateToTry = CONVERGENCE_RATE;
                double rateToStopAt = CONVERGENCE_RATE * MAX_RATE_RETRY_MULTIPLIER;
                boolean pointMoved = false;
                while (true) {
                    
                    // Move the evaluation point by the derivative vector times the current rate attempt
                    for (int l = 0; l < N_DIMENSIONS; l++) {
                        double newValue = movingCoord[l] + currentDerivativeVector[l] * rateToTry;
                        if (newValue > 1) newValue = 1;
                        else if (newValue < 0) newValue = 0;
                        if (newValue != movingCoord[l]) pointMoved = true;
                        movingCoord[l] = newValue;
                    }
                    
                    // Every time the movement drops by an order of magnitude, report the status in the console
                    int exponent = Math.getExponent(currentDerivativeMagnitudeSq);
                    if (exponent < lastExponent) {
                        lastExponent = exponent;
                        System.out.println("Current derivative magnitude: " + Math.sqrt(currentDerivativeMagnitudeSq) + ", current value: " + currentValue + ", current max value: " + maxValue);
                    }
                    
                    // If the point moved, exit this loop so we can continue the gradient ascent loop.
                    if (pointMoved) break;
                    
                    // Otherwise, if we've just tried the max rate possible, finish this round of gradient ascent.
                    if (rateToTry >= rateToStopAt) {
                        System.out.println("Convergence condition met.");
                        break;
                    }
                    
                    // Otherwise try moving by a bigger amount.
                    rateToTry *= 2;
                }
                
                // If it moved, don't execute the code below, which exits the gradient ascent round.
                if (pointMoved) continue;
                
                // Print updated results following this round of gradient ascent.
                System.out.println();
                System.out.println("Max Value Found: " + maxValue);
                System.out.println("Location: " + Arrays.toString(maximizingCoord));
                System.out.println("Starting Location: " + Arrays.toString(maximizingStartingCoord));
                System.out.println("Gradient Indices: " + Arrays.toString(maximizingGradientIndices));
                System.out.println("Derivative Vector: " + Arrays.toString(maximizingDerivativeVector));
                System.out.println();
                break;
                
            }
        }
    }
}