package dev.mattidragon.jsonpatcher.lang.runtime.environment;

import dev.mattidragon.jsonpatcher.lang.ast.Program;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.parse.Parser;
import dev.mattidragon.jsonpatcher.lang.runtime.environment.impl.ProgramDataImpl;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public interface ProgramData {
    Program program();

    TreeMetadata metadata();

    String scriptName();

    String className();

    List<LibraryGroup> allowedLibraries();

    static Builder builder(Program program, TreeMetadata metadata) {
        return new Builder(program, metadata);
    }

    static Builder builder(Parser.Result parseResult) {
        return new Builder(parseResult.program(), parseResult.treeMetadata());
    }

    class Builder {
        private final Program program;
        private final TreeMetadata metadata;
        private @Nullable String scriptName;
        private @Nullable String className;
        private final List<LibraryGroup> allowedLibraries = new ArrayList<>();

        private Builder(Program program, TreeMetadata metadata) {
            this.program = program;
            this.metadata = metadata;
            allowedLibraries.add(LibraryGroup.DEFAULT);
        }

        public Builder scriptName(String scriptName) {
            this.scriptName = scriptName;
            if (className == null) {
                className = scriptName.replaceAll("[^a-zA-Z0-9_/$]", "_");
            }
            return this;
        }

        public Builder className(String className) {
            this.className = className;
            return this;
        }

        public Builder allowLibraryGroup(LibraryGroup libraryGroup) {
            allowedLibraries.add(libraryGroup);
            return this;
        }

        public ProgramData build() {
            if (scriptName == null) {
                throw new IllegalStateException("Script name must be set");
            }
            if (className == null) {
                throw new IllegalStateException("Class name must be set");
            }
            return new ProgramDataImpl(program, metadata, scriptName, className, allowedLibraries);
        }
    }
}
