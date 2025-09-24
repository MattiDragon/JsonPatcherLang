package dev.mattidragon.jsonpatcher.lang.test;

import dev.mattidragon.jsonpatcher.lang.test.sharedTests.FunctionTests;
import dev.mattidragon.jsonpatcher.lang.test.sharedTests.RootTests;
import dev.mattidragon.jsonpatcher.lang.test.sharedTests.VariableTests;
import dev.mattidragon.jsonpatcher.lang.test.sharedTests.expression.BinaryExpressionTests;
import dev.mattidragon.jsonpatcher.lang.test.sharedTests.expression.ModificationExpressionTests;
import dev.mattidragon.jsonpatcher.lang.test.sharedTests.expression.UnaryExpressionTests;
import dev.mattidragon.jsonpatcher.lang.test.sharedTests.statement.ConditionalTests;
import dev.mattidragon.jsonpatcher.lang.test.sharedTests.statement.DeleteStatementTests;
import dev.mattidragon.jsonpatcher.lang.test.sharedTests.statement.ImportStatementTests;
import dev.mattidragon.jsonpatcher.lang.test.sharedTests.statement.LoopStatementTests;
import dev.mattidragon.jsonpatcher.lang.test.sharedTests.stdlib.ArraysLibTests;
import dev.mattidragon.jsonpatcher.lang.test.sharedTests.stdlib.DebugLibTests;
import dev.mattidragon.jsonpatcher.lang.test.sharedTests.stdlib.ValuesLibTests;

public interface AllSharedTests extends
        FunctionTests,
        ImportStatementTests,
        LoopStatementTests,
        BinaryExpressionTests,
        ModificationExpressionTests,
        UnaryExpressionTests,
        RootTests,
        VariableTests,
        ConditionalTests,
        DeleteStatementTests,
        ArraysLibTests,
        ValuesLibTests,
        DebugLibTests
{
}
