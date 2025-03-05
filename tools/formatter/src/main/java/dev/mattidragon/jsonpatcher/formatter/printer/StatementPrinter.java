package dev.mattidragon.jsonpatcher.formatter.printer;

import dev.mattidragon.jsonpatcher.lang.ast.expression.BooleanExpression;
import dev.mattidragon.jsonpatcher.lang.ast.expression.Expression;
import dev.mattidragon.jsonpatcher.lang.ast.expression.FunctionExpression;
import dev.mattidragon.jsonpatcher.lang.ast.function.FunctionArgument;
import dev.mattidragon.jsonpatcher.lang.ast.function.FunctionArguments;
import dev.mattidragon.jsonpatcher.lang.ast.meta.MetadataKey;
import dev.mattidragon.jsonpatcher.lang.ast.statement.*;
import dev.mattidragon.jsonpatcher.lang.parse.Token;
import dev.mattidragon.jsonpatcher.lang.parse.Token.KeywordToken;
import dev.mattidragon.jsonpatcher.lang.parse.Token.SimpleToken;

import java.util.List;

public class StatementPrinter {
    public static void prettyPrint(Statement s, PrintTarget target) {
        if (target.isClosed()) return;
        switch (s) {
            case ExpressionStatement(var expression) -> ExpressionPrinter.prettyPrint(expression, target).write(SimpleToken.SEMICOLON);
            case ReturnStatement(var value) -> {
                target.write(KeywordToken.RETURN);
                value.ifPresent(expression -> {
                    target.space();
                    ExpressionPrinter.prettyPrint(expression, target);
                });
                target.write(SimpleToken.SEMICOLON);
            }
            case ApplyStatement(var root, var action) -> {
                target.write(KeywordToken.APPLY).space().write(SimpleToken.BEGIN_PAREN);
                ExpressionPrinter.prettyPrint(root, target).write(SimpleToken.END_PAREN).space();
                prettyPrint(action, target);
            }
            case DeleteStatement(var ref) -> {
                target.write(KeywordToken.DELETE).space();
                ExpressionPrinter.prettyPrint(ref, target).write(SimpleToken.SEMICOLON);
            }
            case VariableCreationStatement(var name, var initializer, var mutable) -> {
                target.write(mutable ? KeywordToken.VAR : KeywordToken.VAL).space();
                target.write(new Token.WordToken(name)).space();
                target.write(SimpleToken.ASSIGN).space();
                ExpressionPrinter.prettyPrint(initializer, target).write(SimpleToken.SEMICOLON);
            }
            case ContinueStatement() -> target.write(KeywordToken.CONTINUE).write(SimpleToken.SEMICOLON);
            case BreakStatement() -> target.write(KeywordToken.BREAK).write(SimpleToken.SEMICOLON);
            case ForLoopStatement(var initializer, var condition, var incrementer, var body) -> {
                target.write(KeywordToken.FOR).space().write(SimpleToken.BEGIN_PAREN);
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
                target.write(KeywordToken.WHILE).space().write(SimpleToken.BEGIN_PAREN);
                ExpressionPrinter.prettyPrint(condition, target);
                target.write(SimpleToken.END_PAREN).space();
                prettyPrint(body, target);
            }
            case ForEachLoopStatement(var iterable, var variableName, var body) -> {
                target.write(KeywordToken.FOREACH).space().write(SimpleToken.BEGIN_PAREN);
                target.write(new Token.WordToken(variableName)).space().write(KeywordToken.IN).space();
                ExpressionPrinter.prettyPrint(iterable, target).write(SimpleToken.END_PAREN).space();
                prettyPrint(body, target);
            }
            case FunctionDeclarationStatement(var name, FunctionExpression(var body, var args)) -> {
                target.write(KeywordToken.FUNCTION).space().write(new Token.WordToken(name));
                writeArgList(args, target);
                var blockBody = body instanceof BlockStatement blockStatement ? blockStatement : new BlockStatement(List.of(body));
                target.space();
                prettyPrint(blockBody, target);
            }
            case ImportStatement(var libraryName, var variableName) -> {
                if (libraryName.equals(variableName)) {
                    target.write(KeywordToken.IMPORT).space()
                            .write(new Token.StringToken(libraryName))
                            .write(SimpleToken.SEMICOLON);
                } else {
                    target.write(KeywordToken.IMPORT).space()
                            .write(new Token.StringToken(libraryName)).space()
                            .write(KeywordToken.AS).space()
                            .write(new Token.WordToken(variableName))
                            .write(SimpleToken.SEMICOLON);
                }
            }
            case EmptyStatement() -> target.write(SimpleToken.SEMICOLON);
            case BlockStatement(var children) -> {
                target.write(SimpleToken.BEGIN_CURLY);
                target.pushIndent().newLine();

                // Print children, accounting for extra newlines between them in the input
                writeStatementsWithSpacing(target, children);

                target.popIndent().newLine();
                target.write(SimpleToken.END_CURLY);
            }
            case IfStatement(Expression condition, Statement action, Statement elseAction) -> {
                target.write(KeywordToken.IF).space().write(SimpleToken.BEGIN_PAREN);
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

    public static void writeStatementsWithSpacing(PrintTarget target, List<Statement> children) {
        var first = true;
        var prevEndLine = -1;
        for (var child : children) {
            var fullPos = target.getMetadata(child, MetadataKey.FULL_POS);
            var offset = 1;
            if (prevEndLine != -1 && fullPos.isPresent()) {
                offset = Math.max(fullPos.get().from().row() - prevEndLine, 1);
            }
            if (first) {
                offset = 0;
                first = false;
            }
            for (int i = 0; i < offset; i++) {
                target.newLine();
            }
            prettyPrint(child, target);
            prevEndLine = fullPos.map(span -> span.to().row()).orElse(-1);
        }
    }

    public static void writeArgList(FunctionArguments functionArguments, PrintTarget target) {
        var args = functionArguments.arguments();
        var varargs = functionArguments.varargs();

        target.write(SimpleToken.BEGIN_PAREN);
        for (var i = 0; i < args.size(); i++) {
            var arg = args.get(i);
            target.write(switch (arg.target()) {
                case FunctionArgument.Target.Root root -> SimpleToken.DOLLAR;
                case FunctionArgument.Target.Variable(var varName) -> new Token.WordToken(varName);
            });
            arg.defaultValue().ifPresent(defaultValue -> {
                target.space().write(SimpleToken.ASSIGN).space();
                ExpressionPrinter.prettyPrint(defaultValue, target);
            });
            if (i < args.size() - 1) {
                target.write(SimpleToken.COMMA).space();
            } else if (varargs) {
                target.write(SimpleToken.STAR);
            }
        }
        target.write(SimpleToken.END_PAREN);
    }
}
