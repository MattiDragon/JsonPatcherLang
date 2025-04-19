package dev.mattidragon.jsonpatcher.server.index;

import dev.mattidragon.jsonpatcher.docs.newdocs.DocCommentHandler;
import dev.mattidragon.jsonpatcher.lang.analysis.typecheck.TypeChecker;
import dev.mattidragon.jsonpatcher.lang.analysis.variable.VariableAnalyser;
import dev.mattidragon.jsonpatcher.lang.ast.SourceFile;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.error.DiagnosticsBuilder;
import dev.mattidragon.jsonpatcher.lang.parse.Lexer;
import dev.mattidragon.jsonpatcher.lang.parse.Parser;
import dev.mattidragon.jsonpatcher.server.index.typing.DocTypeConverter;
import dev.mattidragon.jsonpatcher.server.index.typing.PreTypingPass;

public class BackgroundIndex extends AstIndex {
    public void index(SourceFile file, DocTypeConverter types) {
        var diagnostics = new DiagnosticsBuilder();
        var metadata = new TreeMetadata();
        var docHandler = new DocCommentHandler(diagnostics, metadata);

        var lex = Lexer.lex(file.code(), file.name(), diagnostics, docHandler);
        var parse = Parser.parse(lex.tokens(), diagnostics);
        var program = parse.program();

        VariableAnalyser.analyse(program, metadata, diagnostics, types.getGlobalNames());
        PreTypingPass.apply(program, metadata, types, diagnostics);
        TypeChecker.typeCheck(program, metadata, diagnostics);

        indexTree(program, metadata);
    }
}
