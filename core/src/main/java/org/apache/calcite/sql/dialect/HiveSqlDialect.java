/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.calcite.sql.dialect;

import org.apache.calcite.config.NullCollation;
import org.apache.calcite.sql.SqlBinaryOperator;
import org.apache.calcite.sql.SqlCall;
import org.apache.calcite.sql.SqlDialect;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.SqlOperator;
import org.apache.calcite.sql.SqlSyntax;
import org.apache.calcite.sql.SqlWriter;
import org.apache.calcite.sql.type.InferTypes;
import org.apache.calcite.sql.type.OperandTypes;
import org.apache.calcite.sql.type.ReturnTypes;

/**
 * A <code>SqlDialect</code> implementation for the Apache Hive database.
 */
public class HiveSqlDialect extends SqlDialect {
  public static final SqlDialect DEFAULT =
      new HiveSqlDialect(EMPTY_CONTEXT
          .withDatabaseProduct(DatabaseProduct.HIVE)
          .withNullCollation(NullCollation.LOW));

  private final boolean emulateNullDirection;

  /** Creates a HiveSqlDialect. */
  public HiveSqlDialect(Context context) {
    super(context);
    // Since 2.1.0, Hive natively supports "NULLS FIRST" and "NULLS LAST".
    // See https://issues.apache.org/jira/browse/HIVE-12994.
    emulateNullDirection = (context.databaseMajorVersion() < 2)
        || (context.databaseMajorVersion() == 2
        && context.databaseMinorVersion() < 1);
  }

  @Override protected boolean allowsAs() {
    return false;
  }

  @Override public void unparseOffsetFetch(SqlWriter writer, SqlNode offset,
      SqlNode fetch) {
    unparseFetchUsingLimit(writer, offset, fetch);
  }

  @Override public SqlNode emulateNullDirection(SqlNode node,
      boolean nullsFirst, boolean desc) {
    if (emulateNullDirection) {
      return emulateNullDirectionWithIsNull(node, nullsFirst, desc);
    }

    return null;
  }

  @Override public boolean emulatesFunction(final SqlOperator operator) {
    switch (operator.getName()) {
    case "POSITION":
    case "MOD":
      return true;
    default:
      return false;
    }
  }

  @Override public void unparseSqlFunction(SqlOperator operatorRex, final SqlWriter writer,
      final SqlCall call, final int leftPrec, final int rightPrec) {
    if (operatorRex.getName().equals("POSITION")) {
      final SqlWriter.Frame frame = writer.startFunCall("INSTR");
      writer.sep(",");
      call.operand(1).unparse(writer, leftPrec, rightPrec);
      writer.sep(",");
      call.operand(0).unparse(writer, leftPrec, rightPrec);
      if (3 == call.operandCount()) {
        throw new RuntimeException("3rd operand Not Supported for Function INSTR in Hive");
      }
      writer.endFunCall(frame);
    } else if (operatorRex.getName().equals("MOD")) {
      SqlSyntax.BINARY.unparse(writer, MOD, call, leftPrec, rightPrec);
    } else {
      throw new RuntimeException("Emulation for Function :- " + operatorRex.getName()
          + "is not handled.");
    }
  }

  public static final SqlOperator MOD = new SqlBinaryOperator(
      "%",
      SqlKind.OTHER,
      22,
      true,
      ReturnTypes.ARG0,
      InferTypes.FIRST_KNOWN,
      OperandTypes.ANY_ANY);

  @Override public boolean supportsCharSet() {
    return false;
  }
}

// End HiveSqlDialect.java
