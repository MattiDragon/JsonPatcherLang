package dev.mattidragon.jsonpatcher.cli;

import dev.mattidragon.jsonpatcher.cli.commands.MainCommand;
import picocli.CommandLine;

public class Main {
    public static void main(String[] args) {
        System.exit(new CommandLine(new MainCommand())
                .setColorScheme(CommandLine.Help.defaultColorScheme(CommandLine.Help.Ansi.AUTO))
                .execute(args));
    }
}
