package com.bart.maris.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class RecordParserTest {

    @Test
    void xdcontrolLayoutIs122Bytes() {
        String src = """
                package MRSCommonCode;
                Record XDCONTROL type basicRecord
                    10 UMSGCODE char(20); // Message Code
                    10 UMSGINS char(25)[3]; // Message Insert
                    10 URC num(3); // Return Code
                    10 UROWCNT smallint; // Row Count
                    10 UUP char(1);
                    10 UDOWN char(1);
                    10 UDIR char(1);
                    10 UCONVERT char(1);
                    10 UUSERID char(8);
                    10 UFUNCTION char(10);
                end // end XDCONTROL
                """;
        RecordDef r = RecordParser.parse(src, "x.egl").get(0);
        assertEquals("MRSCommonCode.XDCONTROL", r.qualifiedName());
        assertEquals(122, r.length);
        Field ins = r.fields.get(1);
        assertEquals(20, ins.offset);
        assertEquals(3, ins.occurs);
        Field rowcnt = r.fields.get(3);
        assertEquals(98, rowcnt.offset);
        assertEquals(2, rowcnt.bytes);
        assertEquals("umsgcode", r.fields.get(0).json);
    }

    @Test
    void substructureArraysFillersAndEndInNames() {
        String src = """
                package P;
                Record R type basicRecord
                    5 DATA_ROW char(24)[2];
                        10 EQUIP-MFR char(20);
                        10 * char(1);
                        10 WS-END-DT char(3);
                    5 AMT decimal(7,2)
                      { column = "T1.AMT", isSQLNullable = yes };
                    5 CNT int;
                end
                """;
        RecordDef r = RecordParser.parse(src, "x.egl").get(0);
        assertTrue(r.fixed, r.problems.toString());
        assertEquals(48 + 4 + 4, r.length);
        Field row = r.fields.get(0);
        List<Field> kids = row.children;
        assertEquals(3, kids.size());
        assertEquals(21, kids.get(2).offset);
        assertEquals("wsEndDt", kids.get(2).json);
        assertEquals(48, r.fields.get(1).offset);
        assertEquals(4, r.fields.get(1).bytes);
    }

    @Test
    void stringFieldsAreNotFixedLayout() {
        String src = """
                package P;
                Record PROGRAM_RETURN_REC type basicRecord
                    errorCode string;
                end
                """;
        assertFalse(RecordParser.parse(src, "x.egl").get(0).fixed);
    }

    @Test
    void browserFileNamesEscapeHyphens() {
        assertEquals("XJULIAN_002dFIX", EglCatalogGenerator.jsName("XJULIAN-FIX"));
        assertEquals("NUM(3,0)", EglCatalogGenerator.normalise("NUM(3)"));
        assertEquals("CHAR(25)[3]", EglCatalogGenerator.normalise("CHAR(25)[3]"));
    }
}
