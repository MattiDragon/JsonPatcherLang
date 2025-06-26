package dev.mattidragon.jsonpatcher.cli.commands;

import dev.mattidragon.jsonpatcher.cli.impl.VersionProvider;
import picocli.CommandLine.Command;

@Command(name = "jsonpatcher",
        description = "JsonPatcher CLI Tools",
        subcommands = {DocsCommand.class, AstCommand.class, FormatCommand.class},
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class)
public class MainCommand {
}
