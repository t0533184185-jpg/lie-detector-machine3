package com.example.demo.audio;

public class ViterbiDecoder {
    // מטריצת מעברים מהפסאודו-קוד שלך
// שנות את זה ב-ViterbiDecoder.java לערכים המכוילים:
    private static final double[][] TRANSITION = {
            {0.60, 0.40},  // מאמת לאמת: 60%, מאמת לשקר: 40%
            {0.25, 0.75}   // משקר לאמת: 25%, משקר לשקר: 75%
    };

    private static final double[] INITIAL = {0.5, 0.5};
    private static final String[] STATE_NAMES = {"אמת", "שקר"};

    public String[] decode(double[][] emissionProbs) {
        int T = emissionProbs.length;
        int N = 2; // מספר מצבים

        double[][] viterbi = new double[T][N];
        int[][] backpointer = new int[T][N];

        // אתחול
        for (int s = 0; s < N; s++) {
            viterbi[0][s] = Math.log(INITIAL[s]) + Math.log(emissionProbs[0][s] + 1e-10);
        }

        // לולאה קדימה
        for (int t = 1; t < T; t++) {
            for (int s = 0; s < N; s++) {
                double maxProb = Double.NEGATIVE_INFINITY;
                int bestPrev = 0;
                for (int prev = 0; prev < N; prev++) {
                    double prob = viterbi[t-1][prev] + Math.log(TRANSITION[prev][s])
                            + Math.log(emissionProbs[t][s] + 1e-10);
                    if (prob > maxProb) {
                        maxProb = prob;
                        bestPrev = prev;
                    }
                }
                viterbi[t][s] = maxProb;
                backpointer[t][s] = bestPrev;
            }
        }

        // backtracking
        String[] result = new String[T];
        int lastState = viterbi[T-1][0] > viterbi[T-1][1] ? 0 : 1;
        result[T-1] = STATE_NAMES[lastState];
        for (int t = T-2; t >= 0; t--) {
            lastState = backpointer[t+1][lastState];
            result[t] = STATE_NAMES[lastState];
        }
        return result;
    }
}