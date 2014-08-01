package nl.esciencecenter.ncradar;

import static org.junit.Assert.assertEquals;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import org.junit.Before;
import org.junit.Test;


public class TestJNICalcTexture extends JNIMethodsVol2Bird {

    private int[] texImage;
    private int[] dbzImage;
    private int[] vradImage;
    private int nRangNeighborhood;
    private int nAzimNeighborhood;
    private int nCountMin;
    private float texOffset;
    private float texScale;
    private float dbzOffset;
    private float dbzScale;
    private float vradOffset;
    private float vradScale;
    private int vradMissing;
    private int nRang;
    private int nAzim;



    private double calcMean(double[] array) {

        int nSamples = array.length;
        return calcSum(array) / nSamples;
    }



    private double calcStdDev(double[] array) {

        int nSamples = array.length;
        double[] squaredDevs = new double[nSamples];

        double mean = calcMean(array);

        for (int iSample = 0; iSample < nSamples; iSample++) {
            squaredDevs[iSample] = Math.pow(array[iSample] - mean, 2);
        }

        double stdDev = Math.sqrt(calcMean(squaredDevs));
        return stdDev;

    }



    private double calcSum(double[] array) {

        int nSamples = array.length;
        double sum = 0;

        for (int iSample = 0; iSample < nSamples; iSample++) {
            sum += array[iSample];
        }
        return sum;

    }



    private int[] readDataFromFile(String filename) throws IOException {

        List<Integer> arrList = new ArrayList<Integer>();

        BufferedReader reader = new BufferedReader(new FileReader(filename));

        String line;

        while ((line = reader.readLine()) != null) {

            Scanner scanner = new Scanner(line);
            while (scanner.hasNext()) {
                int nextInt = scanner.nextInt();
                arrList.add(nextInt);
            }

        }
        reader.close();

        int nElems = arrList.size();
        int[] outputArray = new int[nElems];
        for (int iElem = 0; iElem < nElems; iElem++) {
            outputArray[iElem] = arrList.get(iElem).intValue();
        }

        return outputArray;
    }



    private int[][] reshapeTo2D(int[] inputArray, int nRows, int nCols) {

        int[][] outputArray = new int[nRows][nCols];
        int iGlobal = 0;
        for (int iRow = 0; iRow < nRows; iRow++) {
            for (int iCol = 0; iCol < nCols; iCol++) {

                outputArray[iRow][iCol] = inputArray[iGlobal];
                iGlobal += 1;

            }
        }

        return outputArray;

    }



    @Before
    public void setUp() throws Exception {

        texImage = new int[] { 0, 0, 0, 0,
                0, 0, 0, 0,
                0, 0, 0, 0,
                0, 0, 0, 0,
                0, 0, 0, 0 };
        dbzImage = new int[] { 3, 46, 56, 7,
                84, 6, 78, 72,
                3, 5, 23, 56,
                67, 23, 5, 67,
                89, 7, 2, 5 };
        vradImage = new int[] { 8, 94, 56, 65,
                23, 2, 46, 73,
                5, 6, 34, 53,
                0, 89, 42, 3,
                92, 5, 9, 79 };
        nRangNeighborhood = 3;
        nAzimNeighborhood = 3;
        nCountMin = 0;
        texOffset = 0.0f;
        texScale = 1.0f;
        dbzOffset = 0.0f;
        dbzScale = 1.0f;
        vradOffset = 0.0f;
        vradScale = 1.0f;
        vradMissing = 255;
        nRang = 4;
        nAzim = 5;

    }



    @Test
    public void testNativeCalcTexture1() throws Exception {

        // this test reproduces calculation of standard deviation as used
        // in the C code (which is not obviously correct)

        calcTexture(texImage,
                dbzImage,
                vradImage,
                nRangNeighborhood,
                nAzimNeighborhood,
                nCountMin,
                texOffset,
                texScale,
                dbzOffset,
                dbzScale,
                vradOffset,
                vradScale,
                vradMissing,
                nRang,
                nAzim);

        int[][] actual = reshapeTo2D(texImage.clone(), nAzim, nRang);

        int[][] texImage2D = new int[nAzim][nRang];
        int[][] vradImage2D = reshapeTo2D(vradImage, nAzim, nRang);

        for (int iAzim = 0; iAzim < nAzim; iAzim++) {
            for (int iRang = 0 + nRangNeighborhood / 2; iRang < nRang - nRangNeighborhood / 2; iRang++) {

                double vmoment1 = 0;
                double vmoment2 = 0;
                float vRadDiff;

                for (int iAzimNeighborhood = 0; iAzimNeighborhood < nAzimNeighborhood; iAzimNeighborhood++) {
                    for (int iRangNeighborhood = 0; iRangNeighborhood < nRangNeighborhood; iRangNeighborhood++) {

                        int iAzimDelta = iAzimNeighborhood - (nAzimNeighborhood / 2);
                        int iRangDelta = iRangNeighborhood - (nRangNeighborhood / 2);
                        int iAzimLocal = (nAzim + iAzim + iAzimDelta) % nAzim;
                        int iRangLocal = iRang + iRangDelta;

                        vRadDiff = vradOffset + vradScale *
                                (vradImage2D[iAzim][iRang] - vradImage2D[iAzimLocal][iRangLocal]);

                        vmoment1 += vRadDiff;
                        vmoment2 += Math.pow(vRadDiff, 2);

                    }
                }

                vmoment1 /= (nAzimNeighborhood * nRangNeighborhood);
                vmoment2 /= (nAzimNeighborhood * nRangNeighborhood);

                double tex = Math.sqrt(Math.abs(vmoment2 - Math.pow(vmoment1, 2)));
                texImage2D[iAzim][iRang] = (byte) Math.round((tex - texOffset) / texScale);

                // FIXME signed byte != unsigned char

            }
        }

        int[][] expected = texImage2D.clone();

        for (int iAzim = 0; iAzim < nAzim; iAzim++) {
            for (int iRang = 1; iRang < nRang - 1; iRang++) {

                assertEquals(expected[iAzim][iRang], actual[iAzim][iRang], 1);

            }
        }

    }



    @Test
    public void testNativeCalcTexture2() throws Exception {

        // this test should reproduce calculation of standard deviation

        calcTexture(texImage,
                dbzImage,
                vradImage,
                nRangNeighborhood,
                nAzimNeighborhood,
                nCountMin,
                texOffset,
                texScale,
                dbzOffset,
                dbzScale,
                vradOffset,
                vradScale,
                vradMissing,
                nRang,
                nAzim);

        int[][] actual = reshapeTo2D(texImage.clone(), nAzim, nRang);

        int[][] texImage2D = new int[nAzim][nRang];
        int[][] vradImage2D = reshapeTo2D(vradImage, nAzim, nRang);

        for (int iAzim = 0; iAzim < nAzim; iAzim++) {
            for (int iRang = 0 + nRangNeighborhood / 2; iRang < nRang - nRangNeighborhood / 2; iRang++) {

                int iLocal = 0;
                double[] neighborhoodData = new double[nAzimNeighborhood * nRangNeighborhood];

                for (int iAzimNeighborhood = 0; iAzimNeighborhood < nAzimNeighborhood; iAzimNeighborhood++) {
                    for (int iRangNeighborhood = 0; iRangNeighborhood < nRangNeighborhood; iRangNeighborhood++) {

                        int iAzimDelta = iAzimNeighborhood - (nAzimNeighborhood / 2);
                        int iRangDelta = iRangNeighborhood - (nRangNeighborhood / 2);
                        int iAzimLocal = (nAzim + iAzim + iAzimDelta) % nAzim;
                        int iRangLocal = iRang + iRangDelta;

                        neighborhoodData[iLocal] = vradOffset + vradScale * vradImage2D[iAzimLocal][iRangLocal];
                        iLocal += 1;

                    }
                }

                double stdDev = calcStdDev(neighborhoodData);

                texImage2D[iAzim][iRang] = (byte) Math.round(((stdDev - texOffset) / texScale));

            }
        }

        int[][] expected = texImage2D.clone();

        for (int iAzim = 0; iAzim < nAzim; iAzim++) {
            for (int iRang = 1; iRang < nRang - 1; iRang++) {

                assertEquals(expected[iAzim][iRang], actual[iAzim][iRang], 1);

            }
        }

    }



    @Test
    public void testNativeCalcTexture3() throws Exception {

        // this test should reproduce calculation of standard deviation
        String startDir = System.getProperty("user.dir"); // FIXME wont work on
                                                          // the jenkins
        System.err.println(startDir);

        int[] texImage = readDataFromFile(startDir + "/data/case1/testdata-12x11-pattern0.txt");
        int[] dbzImage = readDataFromFile(startDir + "/data/case1/testdata-12x11-pattern-dbz.txt");
        int[] vradImage = readDataFromFile(startDir + "/data/case1/testdata-12x11-pattern-vrad.txt");

        int nAzim = 12;
        int nRang = 11;

        calcTexture(texImage,
                dbzImage,
                vradImage,
                nRangNeighborhood,
                nAzimNeighborhood,
                nCountMin,
                texOffset,
                texScale,
                dbzOffset,
                dbzScale,
                vradOffset,
                vradScale,
                vradMissing,
                nRang,
                nAzim);

        int[][] actual = reshapeTo2D(texImage.clone(), nAzim, nRang);

        int[][] texImage2D = new int[nAzim][nRang];
        int[][] vradImage2D = reshapeTo2D(vradImage, nAzim, nRang);

        for (int iAzim = 0; iAzim < nAzim; iAzim++) {
            for (int iRang = 0 + nRangNeighborhood / 2; iRang < nRang - nRangNeighborhood / 2; iRang++) {

                int iLocal = 0;
                double[] neighborhoodData = new double[nAzimNeighborhood * nRangNeighborhood];

                for (int iAzimNeighborhood = 0; iAzimNeighborhood < nAzimNeighborhood; iAzimNeighborhood++) {
                    for (int iRangNeighborhood = 0; iRangNeighborhood < nRangNeighborhood; iRangNeighborhood++) {

                        int iAzimDelta = iAzimNeighborhood - (nAzimNeighborhood / 2);
                        int iRangDelta = iRangNeighborhood - (nRangNeighborhood / 2);
                        int iAzimLocal = (nAzim + iAzim + iAzimDelta) % nAzim;
                        int iRangLocal = iRang + iRangDelta;

                        neighborhoodData[iLocal] = vradOffset + vradScale * vradImage2D[iAzimLocal][iRangLocal];
                        iLocal += 1;

                    }
                }

                double stdDev = calcStdDev(neighborhoodData);

                texImage2D[iAzim][iRang] = (byte) Math.round(((stdDev - texOffset) / texScale));

            }
        }

        int[][] expected = texImage2D.clone();

        for (int iAzim = 0; iAzim < nAzim; iAzim++) {
            for (int iRang = 1; iRang < nRang - 1; iRang++) {

                assertEquals(expected[iAzim][iRang], actual[iAzim][iRang], 1);

            }
        }

    }

}
