/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.util;

import java.util.*;
import java.util.function.DoubleUnaryOperator;

/**
 * Recursive descent arithmetic compiler. No scripting engine or arbitrary code execution.
 *
 * <p>Compiles recursive-descent arithmetic into pure evaluation lambdas. Multiplication binds
 * before addition; exponentiation is right-associative and binds inside unary minus. Only supported
 * variables/functions are accepted.
 */
public final class ExpressionParser {
  /**
   * Pure arithmetic evaluation with x and optional a/b/c parameters. The single-argument overload
   * defaults parameters to 1,0,0; domain errors may return non-finite values for plot discontinuity
   * handling.
   */
  @FunctionalInterface
  public interface Expression {
    /**
     * Evaluates arithmetic with the supplied x and optional a/b/c parameters.
     *
     * @param x mathematical independent variable
     * @param a expression parameter a, defaulting to 1 in the single-argument overload
     * @param b expression parameter b, defaulting to 0
     * @param c expression parameter c, defaulting to 0
     * @return evaluates arithmetic with the supplied x and optional a/b/c parameters
     */
    double evaluate(double x, double a, double b, double c);

    /**
     * Evaluates arithmetic with the supplied x and optional a/b/c parameters.
     *
     * @param x mathematical independent variable
     * @return evaluates arithmetic with the supplied x and optional a/b/c parameters
     */
    default double evaluate(double x) {
      return evaluate(x, 1, 0, 0);
    }
  }

  private final String source;

  private int pos;

  /**
   * Creates a configured ExpressionParser instance.
   *
   * @param source expression source text or audio source path, according to the overload
   */
  private ExpressionParser(String source) {
    this.source = source;
  }

  /**
   * Compiles validated arithmetic source into a pure expression evaluator.
   *
   * @param source expression source text or audio source path, according to the overload
   * @return compiles validated arithmetic source into a pure expression evaluator
   * @throws IllegalArgumentException if an input violates the constraints described above
   */
  public static Expression compile(String source) {
    if (source == null || source.isBlank())
      throw new IllegalArgumentException("Expression is empty");
    if (source.length() > 4096) throw new IllegalArgumentException("Expression too long");
    var p = new ExpressionParser(source);
    Expression e = p.sum();
    p.space();
    if (p.pos != source.length()) throw p.error("Unexpected token");
    return e;
  }

  /**
   * Parses left-associative addition and subtraction.
   *
   * @return parses left-associative addition and subtraction
   */
  private Expression sum() {
    Expression result = product();
    while (true) {
      if (take('+')) {
        Expression left = result, right = product();
        result = (x, a, b, c) -> left.evaluate(x, a, b, c) + right.evaluate(x, a, b, c);
      } else if (take('-')) {
        Expression left = result, right = product();
        result = (x, a, b, c) -> left.evaluate(x, a, b, c) - right.evaluate(x, a, b, c);
      } else return result;
    }
  }

  /**
   * Parses multiplication and division before additive operators.
   *
   * @return parses multiplication and division before additive operators
   */
  private Expression product() {
    Expression result = unary();
    while (true) {
      if (take('*')) {
        Expression left = result, right = unary();
        result = (x, a, b, c) -> left.evaluate(x, a, b, c) * right.evaluate(x, a, b, c);
      } else if (take('/')) {
        Expression left = result, right = unary();
        result = (x, a, b, c) -> left.evaluate(x, a, b, c) / right.evaluate(x, a, b, c);
      } else return result;
    }
  }

  /**
   * Parses leading signs while preserving power precedence.
   *
   * @return parses leading signs while preserving power precedence
   */
  private Expression unary() {
    if (take('+')) return unary();
    if (take('-')) {
      Expression arg = unary();
      return (x, a, b, c) -> -arg.evaluate(x, a, b, c);
    }
    return power();
  }

  /**
   * Parses right-associative exponentiation.
   *
   * @return parses right-associative exponentiation
   */
  private Expression power() {
    Expression left = atom();
    if (take('^')) {
      Expression right = unary();
      return (x, a, b, c) -> Math.pow(left.evaluate(x, a, b, c), right.evaluate(x, a, b, c));
    }
    return left;
  }

  /**
   * Parses a literal, variable, supported function or parenthesized expression.
   *
   * @return parses a literal, variable, supported function or parenthesized expression
   * @throws IllegalArgumentException if an input violates the constraints described above
   */
  private Expression atom() {
    space();
    if (take('(')) {
      Expression e = sum();
      expect(')');
      return e;
    }
    int begin = pos;
    if (pos < source.length()
        && (Character.isDigit(source.charAt(pos)) || source.charAt(pos) == '.')) {
      while (pos < source.length()
          && (Character.isDigit(source.charAt(pos)) || source.charAt(pos) == '.')) pos++;
      if (pos < source.length() && (source.charAt(pos) == 'e' || source.charAt(pos) == 'E')) {
        pos++;
        if (pos < source.length() && (source.charAt(pos) == '+' || source.charAt(pos) == '-'))
          pos++;
        while (pos < source.length() && Character.isDigit(source.charAt(pos))) pos++;
      }
      try {
        double n = Checks.finite(Double.parseDouble(source.substring(begin, pos)), "literal");
        return (x, a, b, c) -> n;
      } catch (NumberFormatException ex) {
        throw error("Invalid literal");
      }
    }
    while (pos < source.length() && Character.isLetter(source.charAt(pos))) pos++;
    String name = source.substring(begin, pos);
    if (name.isEmpty()) throw error("Expected number, variable or function");
    return switch (name) {
      case "x" -> (x, a, b, c) -> x;
      case "a" -> (x, a, b, c) -> a;
      case "b" -> (x, a, b, c) -> b;
      case "c" -> (x, a, b, c) -> c;
      case "pi" -> (x, a, b, c) -> Math.PI;
      case "e" -> (x, a, b, c) -> Math.E;
      default -> {
        DoubleUnaryOperator fn =
            switch (name) {
              case "sin" -> Math::sin;
              case "cos" -> Math::cos;
              case "tan" -> Math::tan;
              case "sqrt" -> Math::sqrt;
              case "abs" -> Math::abs;
              case "exp" -> Math::exp;
              case "log" -> Math::log;
              default -> throw error("Unknown function '" + name + "'");
            };
        expect('(');
        Expression arg = sum();
        expect(')');
        yield (x, a, b, c) -> fn.applyAsDouble(arg.evaluate(x, a, b, c));
      }
    };
  }

  /** Skips whitespace before the next token. */
  private void space() {
    while (pos < source.length() && Character.isWhitespace(source.charAt(pos))) pos++;
  }

  /**
   * Consumes the requested token after whitespace if present.
   *
   * @param ch expected delimiter or token character
   * @return true when the documented condition holds; false otherwise
   */
  private boolean take(char ch) {
    space();
    if (pos < source.length() && source.charAt(pos) == ch) {
      pos++;
      return true;
    }
    return false;
  }

  /**
   * Requires and consumes the expected delimiter.
   *
   * @param ch expected delimiter or token character
   */
  private void expect(char ch) {
    if (!take(ch)) throw error("Expected '" + ch + "'");
  }

  /**
   * Creates an exception identifying expression source and the current column.
   *
   * @param s source notation to parse
   * @return creates an exception identifying expression source and the current column
   */
  private IllegalArgumentException error(String s) {
    return new IllegalArgumentException(
        s + " at expression column " + (pos + 1) + " in '" + source + "'");
  }
}
