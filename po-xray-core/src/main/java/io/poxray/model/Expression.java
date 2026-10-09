package io.poxray.model;

import java.util.List;

/**
 * Expression: {@code text} is the editor text ({@code stringExpression}); when that is missing it is rendered
 * from the step tree and {@code rendered} is true. {@code reads} = data paths read, {@code functions} = called
 * functions (built-in or catalog), both in order of appearance. {@code generated} marks expressions created by the
 * editor (source text prefixed with {@code #generated=true#}, the prefix is stripped from {@code text}).
 */
public record Expression(String text, Boolean rendered, Boolean generated, List<String> reads, List<String> functions) {
}
