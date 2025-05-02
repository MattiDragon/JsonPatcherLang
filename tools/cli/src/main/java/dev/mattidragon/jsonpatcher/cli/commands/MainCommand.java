package dev.mattidragon.jsonpatcher.cli.commands;

import dev.mattidragon.jsonpatcher.cli.VersionProvider;
import picocli.CommandLine.*;

@Command(name = "jsonpatcher",
        description = "JsonPatcher CLI Tools",
        subcommands = {DocsCommand.class},
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class)
public class MainCommand {
}
