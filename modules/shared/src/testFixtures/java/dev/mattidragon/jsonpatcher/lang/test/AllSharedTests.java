package dev.mattidragon.jsonpatcher.lang.test;

import dev.mattidragon.jsonpatcher.lang.test.sharedTests.FunctionTests;
import dev.mattidragon.jsonpatcher.lang.test.sharedTests.expression.BinaryExpressionTests;
import dev.mattidragon.jsonpatcher.lang.test.sharedTests.statement.ImportStatementTests;
import dev.mattidragon.jsonpatcher.lang.test.sharedTests.statement.LoopStatementTests;

public interface AllSharedTests extends FunctionTests, ImportStatementTests, LoopStatementTests, BinaryExpressionTests {
}
