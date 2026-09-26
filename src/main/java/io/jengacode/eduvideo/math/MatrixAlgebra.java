/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.math;

/**
 * Small dense numerical matrix operations for authoring validated explanations. Inputs are copied;
 * methods never mutate a caller's matrix. Singular pivots use a 1e-12 threshold and results are
 * double precision, not symbolic or exact rational arithmetic.
 */
public final class MatrixAlgebra {
  /** Prevents utility construction. */
  private MatrixAlgebra() {}

  /**
   * * Copies and validates a nonempty finite rectangular matrix.
   *
   * @param a left finite rectangular numeric matrix
   * @return independent validated matrix copy
   */
  private static double[][] copy(double[][] a) {
    if (a.length == 0 || a[0].length == 0) throw new IllegalArgumentException("Empty matrix");
    double[][] out = new double[a.length][];
    for (int r = 0; r < a.length; r++) {
      if (a[r].length != a[0].length) throw new IllegalArgumentException("Ragged matrix");
      out[r] = a[r].clone();
      for (double v : out[r])
        if (!Double.isFinite(v)) throw new IllegalArgumentException("Nonfinite matrix");
    }
    return out;
  }

  /**
   * * Creates the square identity matrix of positive order.
   *
   * @param n positive identity order or bounded sample count
   * @return new square identity matrix
   */
  public static double[][] identity(int n) {
    if (n < 1) throw new IllegalArgumentException("Order must be positive");
    double[][] a = new double[n][n];
    for (int i = 0; i < n; i++) a[i][i] = 1;
    return a;
  }

  /**
   * * Adds matrices with equal dimensions.
   *
   * @param a left finite rectangular numeric matrix
   * @param b right finite rectangular numeric matrix
   * @return new elementwise sum matrix
   */
  public static double[][] add(double[][] a, double[][] b) {
    var c = copy(a);
    var d = copy(b);
    if (c.length != d.length || c[0].length != d[0].length)
      throw new IllegalArgumentException("Dimension mismatch");
    for (int r = 0; r < c.length; r++) for (int k = 0; k < c[0].length; k++) c[r][k] += d[r][k];
    return c;
  }

  /**
   * * Multiplies every cell by a finite scalar.
   *
   * @param a left finite rectangular numeric matrix
   * @param scalar finite scalar multiplier
   * @return new scalar-multiplied matrix
   */
  public static double[][] scale(double[][] a, double scalar) {
    if (!Double.isFinite(scalar)) throw new IllegalArgumentException("Invalid scalar");
    var c = copy(a);
    for (var row : c) for (int k = 0; k < row.length; k++) row[k] *= scalar;
    return c;
  }

  /**
   * * Transposes rows and columns into a new matrix.
   *
   * @param a left finite rectangular numeric matrix
   * @return new transposed matrix
   */
  public static double[][] transpose(double[][] a) {
    a = copy(a);
    double[][] c = new double[a[0].length][a.length];
    for (int r = 0; r < a.length; r++) for (int k = 0; k < a[0].length; k++) c[k][r] = a[r][k];
    return c;
  }

  /**
   * * Multiplies conformable matrices; vectors are represented by single-column matrices.
   *
   * @param a left finite rectangular numeric matrix
   * @param b right finite rectangular numeric matrix
   * @return new matrix product
   */
  public static double[][] multiply(double[][] a, double[][] b) {
    a = copy(a);
    b = copy(b);
    if (a[0].length != b.length) throw new IllegalArgumentException("Dimension mismatch");
    double[][] c = new double[a.length][b[0].length];
    for (int r = 0; r < c.length; r++)
      for (int k = 0; k < c[0].length; k++)
        for (int j = 0; j < b.length; j++) c[r][k] += a[r][j] * b[j][k];
    return c;
  }

  /**
   * * Swaps two rows without changing the source matrix.
   *
   * @param a left finite rectangular numeric matrix
   * @param first zero-based first row to exchange
   * @param second zero-based second row to exchange
   * @return new matrix with the selected rows exchanged
   */
  public static double[][] swapRows(double[][] a, int first, int second) {
    a = copy(a);
    var row = a[first];
    a[first] = a[second];
    a[second] = row;
    return a;
  }

  /**
   * * Performs target += factor * source, the elementary Gaussian row-addition operation.
   *
   * @param a left finite rectangular numeric matrix
   * @param target zero-based target row receiving the elementary row operation
   * @param source zero-based source row used in the elementary row operation
   * @param factor finite row-addition multiplier
   * @return new matrix after elementary row addition
   */
  public static double[][] addRow(double[][] a, int target, int source, double factor) {
    a = copy(a);
    if (!Double.isFinite(factor)) throw new IllegalArgumentException("Invalid factor");
    for (int c = 0; c < a[0].length; c++) a[target][c] += factor * a[source][c];
    return a;
  }

  /**
   * * Computes a square determinant by pivoted Gaussian elimination.
   *
   * @param a left finite rectangular numeric matrix
   * @return pivoted double-precision determinant
   */
  public static double determinant(double[][] a) {
    a = copy(a);
    if (a.length != a[0].length)
      throw new IllegalArgumentException("Determinant needs square matrix");
    double det = 1;
    for (int c = 0; c < a.length; c++) {
      int pivot = c;
      for (int r = c + 1; r < a.length; r++)
        if (Math.abs(a[r][c]) > Math.abs(a[pivot][c])) pivot = r;
      if (Math.abs(a[pivot][c]) < 1e-12) return 0;
      if (pivot != c) {
        var row = a[c];
        a[c] = a[pivot];
        a[pivot] = row;
        det = -det;
      }
      double v = a[c][c];
      det *= v;
      for (int r = c + 1; r < a.length; r++) {
        double factor = a[r][c] / v;
        for (int k = c + 1; k < a.length; k++) a[r][k] -= factor * a[c][k];
      }
    }
    return det;
  }

  /**
   * * Computes reduced row echelon form using partial pivoting.
   *
   * @param a left finite rectangular numeric matrix
   * @return new reduced row echelon matrix
   */
  public static double[][] rref(double[][] a) {
    a = copy(a);
    int row = 0;
    for (int c = 0; c < a[0].length && row < a.length; c++) {
      int pivot = row;
      for (int r = row + 1; r < a.length; r++)
        if (Math.abs(a[r][c]) > Math.abs(a[pivot][c])) pivot = r;
      if (Math.abs(a[pivot][c]) < 1e-12) continue;
      var temp = a[row];
      a[row] = a[pivot];
      a[pivot] = temp;
      double v = a[row][c];
      for (int k = 0; k < a[0].length; k++) a[row][k] /= v;
      for (int r = 0; r < a.length; r++)
        if (r != row) {
          double f = a[r][c];
          for (int k = 0; k < a[0].length; k++) a[r][k] -= f * a[row][k];
        }
      row++;
    }
    return a;
  }
}
