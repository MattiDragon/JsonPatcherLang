package dev.mattidragon.jsonpatcher.formatter.printer;

import dev.mattidragon.jsonpatcher.lang.analysis.comment.CommentAttacher;
import dev.mattidragon.jsonpatcher.lang.ast.expression.BooleanExpression;
import dev.mattidragon.jsonpatcher.lang.ast.expression.Expression;
import dev.mattidragon.jsonpatcher.lang.ast.expression.FunctionExpression;
import dev.mattidragon.jsonpatcher.lang.ast.function.FunctionArgument;
import dev.mattidragon.jsonpatcher.lang.ast.function.FunctionArguments;
import dev.mattidragon.jsonpatcher.lang.ast.meta.MetadataHolder;
import dev.mattidragon.jsonpatcher.lang.ast.meta.MetadataKey;
import dev.mattidragon.jsonpatcher.lang.ast.statement.*;
import dev.mattidragon.jsonpatcher.lang.parse.Token;
import dev.mattidragon.jsonpatcher.lang.parse.Token.KeywordToken;
import dev.mattidragon.jsonpatcher.lang.parse.Token.SimpleToken;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StatementPrinter {
    public static void prettyPrint(Statement s, PrintTarget target) {
        if (target.isClosed()) return;
        PrintUtils.printAttachedComment(s, target);

        switch (s) {
            case ExpressionStatement(var expression) -> {
                ExpressionPrinter.prettyPrint(expression, target);
                PrintUtils.printAllContainedComments(s, target, true, false);
                target.write(SimpleToken.SEMICOLON);
            }
            case ReturnStatement(var value) -> {
                target.write(KeywordToken.RETURN);
                PrintUtils.printAllContainedComments(s, target, true, true);
                value.ifPresent(expression -> {
                    target.space();
                    ExpressionPrinter.prettyPrint(expression, target);
                });
                target.write(SimpleToken.SEMICOLON);
            }
            case ApplyStatement(var root, var action) -> {
                target.write(KeywordToken.APPLY).space().write(SimpleToken.BEGIN_PAREN);
                PrintUtils.printAllContainedComments(s, target, true, true);
                ExpressionPrinter.prettyPrint(root, target).write(SimpleToken.END_PAREN).space();
                prettyPrint(action, target);
            }
            case DeleteStatement(var ref) -> {
                target.write(KeywordToken.DELETE).space();
                PrintUtils.printAllContainedComments(s, target, true, true);
                ExpressionPrinter.prettyPrint(ref, target).write(SimpleToken.SEMICOLON);
            }
            case VariableCreationStatement(var name, var initializer, var mutable) -> {
                target.write(mutable ? KeywordToken.VAR : KeywordToken.VAL).space();
                target.write(new Token.WordToken(name)).space();
                PrintUtils.printAllContainedComments(s, target, true, false);
                target.write(SimpleToken.ASSIGN).space();
                ExpressionPrinter.prettyPrint(initializer, target).write(SimpleToken.SEMICOLON);
            }
            case ContinueStatement() -> {
                target.write(KeywordToken.CONTINUE);
                PrintUtils.printAllContainedComments(s, target, true, false);
                target.write(SimpleToken.SEMICOLON);
            }
            case BreakStatement() -> {
                target.write(KeywordToken.BREAK);
                PrintUtils.printAllContainedComments(s, target, true, false);
                target.write(SimpleToken.SEMICOLON);
            }
            case ForLoopStatement(var initializer, var condition, var incrementer, var body) -> {
                target.write(KeywordToken.FOR).space();
                PrintUtils.printAllContainedComments(s, target, true, false);
                target.write(SimpleToken.BEGIN_PAREN);
                prettyPrint(initializer, target);
                if (condition instanceof BooleanExpression(var value) && value) {
                    target.write(SimpleToken.SEMICOLON);
                } else {
                    ExpressionPrinter.prettyPrint(condition, target).write(SimpleToken.SEMICOLON);
                }
                prettyPrint(incrementer, target);
                target.write(SimpleToken.END_PAREN).space();
                prettyPrint(body, target);
            }
            case WhileLoopStatement(var condition, var body) -> {
                target.write(KeywordToken.WHILE).space();
                PrintUtils.printAllContainedComments(s, target, true, false);
                target.write(SimpleToken.BEGIN_PAREN);
                ExpressionPrinter.prettyPrint(condition, target);
                target.write(SimpleToken.END_PAREN).space();
                prettyPrint(body, target);
            }
            case ForEachLoopStatement(var iterable, var variableName, var body) -> {
                target.write(KeywordToken.FOREACH).space();
                PrintUtils.printAllContainedComments(s, target, true, false);
                target.write(SimpleToken.BEGIN_PAREN);
                target.write(new Token.WordToken(variableName)).space().write(KeywordToken.IN).space();
                ExpressionPrinter.prettyPrint(iterable, target).write(SimpleToken.END_PAREN).space();
                prettyPrint(body, target);
            }
            case FunctionDeclarationStatement(var name, FunctionExpression(var body, var args)) -> {
                target.write(KeywordToken.FUNCTION).space();
                PrintUtils.printAllContainedComments(s, target, true, false);
                target.write(new Token.WordToken(name));
                writeArgList(args, target);
                var blockBody = body instanceof BlockStatement blockStatement ? blockStatement : new BlockStatement(List.of(body));
                target.space();
                prettyPrint(blockBody, target);
            }
            case ImportStatement(var libraryName, var variableName) -> {
                if (libraryName.equals(variableName)) {
                    target.write(KeywordToken.IMPORT).space()
                            .write(new Token.StringToken(libraryName));
                } else {
                    target.write(KeywordToken.IMPORT).space()
                            .write(new Token.StringToken(libraryName)).space()
                            .write(KeywordToken.AS).space()
                            .write(new Token.WordToken(variableName));
                }
                PrintUtils.printAllContainedComments(s, target, true, false);
                target.write(SimpleToken.SEMICOLON);
            }
            case EmptyStatement() -> target.write(SimpleToken.SEMICOLON);
            case BlockStatement(var children) -> {
                target.write(SimpleToken.BEGIN_CURLY);
                target.pushIndent().newLine();

                // Print children, accounting for extra newlines between them in the input
                writeStatementsWithSpacing(target, children, s);

                target.popIndent().newLine();
                target.write(SimpleToken.END_CURLY);
            }
            case IfStatement(Expression condition, Statement action, Statement elseAction) -> {
                target.write(KeywordToken.IF).space();
                PrintUtils.printAllContainedComments(s, target, true, false);
                target.write(SimpleToken.BEGIN_PAREN);
                ExpressionPrinter.prettyPrint(condition, target);
                target.write(SimpleToken.END_PAREN).space();
                prettyPrint(action, target);
                // TODO: add newline for non-block if
                if (elseAction != null) {
                    target.space().write(KeywordToken.ELSE).space();
                    prettyPrint(elseAction, target);
                }
            }
            default -> target.write(new Token.ErrorToken("Unsupported statement: " + s.getClass().getSimpleName())).write(SimpleToken.SEMICOLON);
        }
    }

    public static void writeStatementsWithSpacing(PrintTarget target, List<Statement> children, MetadataHolder commentHolder) {
        // Contained comments, in reverse order to avoid copying the whole list each time an element is removed
        var comments = new ArrayList<>(target.getMetadata(commentHolder, CommentAttacher.CONTAINED_COMMENTS).orElse(List.of()));
        Collections.reverse(comments);

        var first = true;
        var prevEndLine = -1;
        for (var child : children) {
            var fullPos = target.getMetadata(child, MetadataKey.FULL_POS);

            var commentComparisonLine = (int) fullPos.map(p -> p.from().row()).orElse(Integer.MAX_VALUE);
            var hadComment = false;
            while (!comments.isEmpty() && comments.getLast().comments().getFirst().start().row() < commentComparisonLine) {
                var commentBlock = comments.removeLast();
                if (first) {
                    first = false;
                } else {
                    if (!hadComment) target.newLine();
                    target.newLine();
                }
                for (var comment : commentBlock.comments()) {
                    target.writeCommentLine(comment.text());
                    target.newLine();
                }
                prevEndLine = commentBlock.comments().getLast().start().row() + 1;
                hadComment = true;
            }

            var offset = 1;
            if (prevEndLine != -1 && fullPos.isPresent()) {
                offset = Math.max(fullPos.get().from().row() - prevEndLine, offset);
            }

            offset -= target.getMetadata(child, CommentAttacher.ATTACHED_COMMENT)
                    .map(block -> block.comments().size())
                    .orElse(0);

            if (first) {
                offset = 0;
                first = false;
            }

            if (offset > 2) {
                offset = 2;
            }

            for (int i = 0; i < offset; i++) {
                target.newLine();
            }
            prettyPrint(child, target);
            prevEndLine = fullPos.map(span -> span.to().row()).orElse(-1);
        }

        var hadComment = false;
        while (!comments.isEmpty()) {
            var commentBlock = comments.removeLast();
            if (!first) {
                if (!hadComment) target.newLine();
                target.newLine();
            }
            var firstLine = true;
            for (var comment : commentBlock.comments()) {
                if (firstLine && !hadComment) {
                    firstLine = false;
                } else {
                    target.newLine();
                }
                target.writeCommentLine(comment.text());
            }
            hadComment = true;
        }
    }

    public static void writeArgList(FunctionArguments functionArguments, PrintTarget target) {
        var args = functionArguments.arguments();
        var varargs = functionArguments.varargs();

        // This is kinda awkward, but we don't expect many comments here anyway
        PrintUtils.printAttachedComment(functionArguments, target);

        PrintUtils.printCommaList(
                target,
                functionArguments,
                args,
                target1 -> target1.write(SimpleToken.BEGIN_PAREN),
                (arg, target1, i) -> {
                    PrintUtils.printAttachedComment(arg, target);
                    switch (arg.target()) {
                        case FunctionArgument.Target.Variable variable ->
                                target1.write(new Token.WordToken(variable.name()));
                        case FunctionArgument.Target.Root.INSTANCE ->
                                target1.write(SimpleToken.DOLLAR);
                    }
                    if (i == args.size() - 1 && varargs) {
                        target1.write(SimpleToken.STAR);
                    } else arg.defaultValue().ifPresent(defaultValue -> {
                        target1.space().write(SimpleToken.ASSIGN).space();
                        ExpressionPrinter.prettyPrint(defaultValue, target1);
                    });
                },
                target1 -> target1.write(SimpleToken.END_PAREN)
        );
    }
}
