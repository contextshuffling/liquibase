package liquibase.change

import liquibase.parser.core.ParsedNode
import liquibase.serializer.LiquibaseSerializable
import liquibase.statement.DatabaseFunction
import liquibase.statement.SequenceNextValueFunction
import liquibase.structure.core.*
import spock.lang.Specification
import spock.lang.Unroll

import java.text.ParseException

public class ColumnConfigTest  extends Specification {

    def constructor_everythingSet() {
        when:
        def table = new Table();

        table.setPrimaryKey(new PrimaryKey().addColumnName(0, "colName").setName("pk_name").setTablespace("pk_tablespace"));
        table.getUniqueConstraints().add(new UniqueConstraint().setName("uq1").addColumn(0, "otherCol"));
        table.getUniqueConstraints().add(new UniqueConstraint().setName("uq2").addColumn(0, "colName"));

        table.getOutgoingForeignKeys().add(new ForeignKey().setName("fk1").setForeignKeyColumns("otherCol"));
        table.getOutgoingForeignKeys().add(new ForeignKey().setName("fk2").setForeignKeyColumns("colName").setPrimaryKeyTable(new Table().setName("otherTable")).setPrimaryKeyColumns("id"));

        Column column = new Column();
        column.setName("colName");
        column.setRelation(table);
        column.setAutoIncrementInformation(new Column.AutoIncrementInformation(3, 5));
        column.setType(new DataType("BIGINT"));
        column.setNullable(false);
        column.setDefaultValue(123);
        column.setRemarks("A Test Column");

        ColumnConfig config = new ColumnConfig(column);

        then:
        config.getName() == "colName"
        config.getDefaultValue() == "123"
        config.getRemarks() == "A Test Column"
        config.getType() == "BIGINT"
        assert !config.getConstraints().isNullable()

        assert config.getConstraints().isUnique()
        config.getConstraints().getUniqueConstraintName() == "uq2"

        assert config.getConstraints().isPrimaryKey()
        config.getConstraints().getPrimaryKeyName() == "pk_name"
        config.getConstraints().getPrimaryKeyTablespace() == "pk_tablespace"

        config.getConstraints().getForeignKeyName() == "fk2"
        config.getConstraints().getReferences() == "otherTable(id)"

        assert config.isAutoIncrement()
        config.getStartWith().longValue() == 3
        config.getIncrementBy().longValue() == 5
    }

    def constructor_nothingSet() {
        when:
        def table = new Table();

        Column column = new Column();
        column.setName("colName");
        column.setRelation(table);
        column.setType(new DataType("BIGINT"));

        ColumnConfig config = new ColumnConfig(column);
        config.getName() == "colName"
        
        then:
        config.getDefaultValue() == null
        config.getRemarks() == null
        config.getType() == "BIGINT"
        config.getConstraints().isNullable() == null //nullable could be unknown

        config.getConstraints().isUnique() == false //we know it is unique or not, cannot return null
        config.getConstraints().getUniqueConstraintName() == null

        config.getConstraints().isPrimaryKey() == false //we know it is unique or not, cannot return null
        config.getConstraints().getPrimaryKeyName() == null
        config.getConstraints().getPrimaryKeyTablespace() == null

        config.getConstraints().getForeignKeyName() == null
        config.getConstraints().getReferences() == null

        config.isAutoIncrement() == false  //we know it is unique or not, cannot return null
        config.getStartWith() == null
        config.getIncrementBy() == null
    }

    def constructor_view() {
        when:
        def view = new View();

        Column column = new Column();
        column.setName("colName");
        column.setRelation(view);
        column.setType(new DataType("BIGINT"));

        ColumnConfig config = new ColumnConfig(column);
             
        then:
        config.getName() == "colName"
        config.getType() == "BIGINT"

        config.getConstraints() == null //return null constraints for views

        config.isAutoIncrement() == null  //set to null for views
    }

    def setValue() throws Exception {
        expect:
        new ColumnConfig().setValue(null).getValue() == null
        new ColumnConfig().setValue("abc").getValue() == "abc"
        new ColumnConfig().setValue("").getValue() == "" // empty strings are saved
        new ColumnConfig().setValue("  not trimmed  ").getValue() == "  not trimmed  " //strings should not be trimmed
        new ColumnConfig().setValue("null").getValue() == "null"
    }

    def setValueNumeric() {
        expect:
        new ColumnConfig().setValueNumeric(3).getValueNumeric() == 3
        new ColumnConfig().setValueNumeric("3").getValueNumeric() == 3L
        new ColumnConfig().setValueNumeric(3.5).getValueNumeric() == 3.5
        new ColumnConfig().setValueNumeric("3.5").getValueNumeric() == 3.5
        new ColumnConfig().setValueNumeric(-6).getValueNumeric() == -6
        new ColumnConfig().setValueNumeric("-6").getValueNumeric() == -6L
        new ColumnConfig().setValueNumeric(0).getValueNumeric() == 0
        new ColumnConfig().setValueNumeric("0").getValueNumeric() == 0L
        new ColumnConfig().setValueNumeric(0.33).getValueNumeric() == 0.33
        new ColumnConfig().setValueNumeric("0.33").getValueNumeric() == 0.33
    }

    def setValueNumeric_null() {
        expect:
        new ColumnConfig().setValueNumeric((String) null).getValueNumeric() == null
        new ColumnConfig().setValueNumeric("null").getValueNumeric() == null
        new ColumnConfig().setValueNumeric("NULL").getValueNumeric() == null
        new ColumnConfig().setValueNumeric((Number) null).getValueNumeric() == null
    }

    def setValueNumeric_wrapped() {
        expect:
        new ColumnConfig().setValueNumeric("(52.3)").getValueNumeric() == 52.3
        new ColumnConfig().setValueNumeric("(-32.3)").getValueNumeric() == -32.3

    }

    def setValueNumeric_function() {
        when:
        ColumnConfig columnConfig = new ColumnConfig().setValueNumeric("max_integer()");
        then:
        columnConfig.getValueNumeric() == null
        columnConfig.getValueComputed().toString() == "max_integer()"

        when:
        columnConfig = new ColumnConfig().setValueNumeric("paramless_fn");
        then:
        columnConfig.getValueNumeric() == null
        columnConfig.getValueComputed().toString() == "paramless_fn"

        when:
        columnConfig = new ColumnConfig().setValueNumeric("fn(3,5)");
        then:
        columnConfig.getValueNumeric() == null
        columnConfig.getValueComputed().toString() == "fn(3,5)"
    }

    def void setValueBoolean() {
        expect:
        new ColumnConfig().setValueBoolean((Boolean) null).getValueBoolean() == null
        new ColumnConfig().setValueBoolean(true).getValueBoolean() == true
        assert !new ColumnConfig().setValueBoolean(false).getValueBoolean()
    }

    def setValueBoolean_string() {
        expect:
        new ColumnConfig().setValueBoolean("null").getValueBoolean() == null
        new ColumnConfig().setValueBoolean("NULL").getValueBoolean() == null
        new ColumnConfig().setValueBoolean((String) null).getValueBoolean() == null
        new ColumnConfig().setValueBoolean("").getValueBoolean() == null
        new ColumnConfig().setValueBoolean(" ").getValueBoolean() == null

        new ColumnConfig().setValueBoolean("true").getValueBoolean() == true
        new ColumnConfig().setValueBoolean("TRUE").getValueBoolean() == true
        new ColumnConfig().setValueBoolean("1").getValueBoolean() == true

        new ColumnConfig().setValueBoolean("false").getValueBoolean() == false
        new ColumnConfig().setValueBoolean("FALSE").getValueBoolean() == false
        new ColumnConfig().setValueBoolean("0").getValueBoolean() == false

        new ColumnConfig().setValueBoolean("bool_val").getValueComputed().toString() == "bool_val"
        new ColumnConfig().setValueBoolean("2").getValueComputed().toString() == "2"
    }


    def setValueComputed() {
        expect:
        new ColumnConfig().setValueComputed(null).getValueComputed() == null
        new ColumnConfig().setValueComputed(new DatabaseFunction("func")).getValueComputed().toString() == "func"
    }

    def setValueSequenceNext() {
        expect:
        new ColumnConfig().setValueSequenceNext(null).getValueSequenceNext() == null
        new ColumnConfig().setValueSequenceNext(new SequenceNextValueFunction("my_seq")).getValueSequenceNext().toString() == "my_seq"
    }

    def setValueDate() {
        expect:
        new ColumnConfig().setValueDate((String) null).getValueDate() == null
        new ColumnConfig().setValueDate("null").getValueDate() == null
        new ColumnConfig().setValueDate("NULL").getValueDate() == null
        new ColumnConfig().setValueDate((Date) null).getValueDate() == null

        Date today = new Date();
        new ColumnConfig().setValueDate(today).getValueDate() == today
        new ColumnConfig().setValueDate("1992-02-11T13:22:44.6").getValueDate().toString() == "1992-02-11 13:22:44.006"
        new ColumnConfig().setValueDate("1992-02-12").getValueDate().toString() == "1992-02-12"

        new ColumnConfig().setValueDate("date_func").getValueComputed().toString() == "date_func"
    }

    def getValueObject() {
        expect:
        new ColumnConfig().setValueBoolean(true).getValueObject() == true
        new ColumnConfig().setValueNumeric(5).getValueObject() == 5
        new ColumnConfig().setValueDate("1993-02-11T13:22:44.006").getValueObject().toString() == "1993-02-11 13:22:44.006"
        new ColumnConfig().setValueComputed(new DatabaseFunction("func")).getValueObject().toString() == "func"
        new ColumnConfig().setValueSequenceNext(new SequenceNextValueFunction("seq_name")).getValueObject().toString() == "seq_name"
        new ColumnConfig().setValueBlobFile("asdg").getValueObject() == "asdg"
        new ColumnConfig().setValueClobFile("zxcv").getValueObject() == "zxcv"
        new ColumnConfig().setValue("A value").getValueObject() == "A value"
        new ColumnConfig().getValueObject() == null
    }

    def setDefaultValueNumeric() throws ParseException {
        expect:
        new ColumnConfig().setDefaultValueNumeric(3).getDefaultValueNumeric() == 3
        new ColumnConfig().setDefaultValueNumeric("3").getDefaultValueNumeric() == 3L
        new ColumnConfig().setDefaultValueNumeric(3.5).getDefaultValueNumeric() == 3.5
        new ColumnConfig().setDefaultValueNumeric("3.5").getDefaultValueNumeric() == 3.5
        new ColumnConfig().setDefaultValueNumeric(-6).getDefaultValueNumeric() == -6
        new ColumnConfig().setDefaultValueNumeric("-6").getDefaultValueNumeric() == -6L
        new ColumnConfig().setDefaultValueNumeric(0).getDefaultValueNumeric() == 0
        new ColumnConfig().setDefaultValueNumeric("0").getDefaultValueNumeric() == 0L
        new ColumnConfig().setDefaultValueNumeric(0.33).getDefaultValueNumeric() == 0.33
        new ColumnConfig().setDefaultValueNumeric("0.33").getDefaultValueNumeric() == 0.33

        new ColumnConfig().setDefaultValueNumeric("new_value()").getDefaultValueComputed().toString() == "new_value()"
    }

    def setDefaultValueNumeric_null() throws ParseException {
        expect:
        new ColumnConfig().setDefaultValueNumeric((String) null).getDefaultValueNumeric() == null
        new ColumnConfig().setDefaultValueNumeric("null").getDefaultValueNumeric() == null
        new ColumnConfig().setDefaultValueNumeric("NULL").getDefaultValueNumeric() == null
        new ColumnConfig().setDefaultValueNumeric((Number) null).getDefaultValueNumeric() == null
    }

    def setDefaultValueNumeric_generatedByDefault() throws ParseException {
        expect:
        ColumnConfig config = new ColumnConfig().setDefaultValueNumeric("GENERATED_BY_DEFAULT");
        config.getDefaultValueNumeric() == null
        assert config.isAutoIncrement()
    }

    def setDefaultNumeric_wrapped() {
        expect:
        new ColumnConfig().setDefaultValueNumeric("(52.3)").getDefaultValueNumeric() == 52.3
        new ColumnConfig().setDefaultValueNumeric("(-32.3)").getDefaultValueNumeric() == -32.3

    }

    def setDefaultValueDate() {
        expect:
        new ColumnConfig().setDefaultValueDate((String) null).getDefaultValueDate() == null
        new ColumnConfig().setDefaultValueDate("null").getDefaultValueDate() == null
        new ColumnConfig().setDefaultValueDate("NULL").getDefaultValueDate() == null
        new ColumnConfig().setDefaultValueDate((Date) null).getDefaultValueDate() == null
        new ColumnConfig().setDefaultValueDate("").getDefaultValueDate() == null

        Date today = new Date();
        new ColumnConfig().setDefaultValueDate(today).getDefaultValueDate() == today
        new ColumnConfig().setDefaultValueDate("1992-02-11T13:22:44.6").getDefaultValueDate().toString() == "1992-02-11 13:22:44.006"
        new ColumnConfig().setDefaultValueDate("1992-02-12").getDefaultValueDate().toString() == "1992-02-12"

        new ColumnConfig().setDefaultValueDate("date_func").getDefaultValueComputed().toString() == "date_func"
    }

    def setDefaultValue() {
        expect:
        new ColumnConfig().setDefaultValue(null).getDefaultValue() == null
        new ColumnConfig().setDefaultValue("abc").getDefaultValue() == "abc"
        new ColumnConfig().setDefaultValue("  abc  ").getDefaultValue() == "  abc  "
        new ColumnConfig().setDefaultValue("null").getDefaultValue() == "null"
        new ColumnConfig().setDefaultValue("").getDefaultValue() == ""
    }


    def setDefaultValueBoolean() {
        expect:
        new ColumnConfig().setDefaultValueBoolean((Boolean) null).getDefaultValueBoolean() == null
        new ColumnConfig().setDefaultValueBoolean(true).getDefaultValueBoolean() == true
        assert !new ColumnConfig().setDefaultValueBoolean(false).getDefaultValueBoolean()
    }

    def setDefaultValueBoolean_string() {
        expect:
        new ColumnConfig().setDefaultValueBoolean("null").getDefaultValueBoolean() == null
        new ColumnConfig().setDefaultValueBoolean("NULL").getDefaultValueBoolean() == null
        new ColumnConfig().setDefaultValueBoolean((String) null).getDefaultValueBoolean() == null
        new ColumnConfig().setDefaultValueBoolean("").getDefaultValueBoolean() == null
        new ColumnConfig().setDefaultValueBoolean(" ").getDefaultValueBoolean() == null

        new ColumnConfig().setDefaultValueBoolean("true").getDefaultValueBoolean() == true
        new ColumnConfig().setDefaultValueBoolean("TRUE").getDefaultValueBoolean() == true
        new ColumnConfig().setDefaultValueBoolean("1").getDefaultValueBoolean() == true

        new ColumnConfig().setDefaultValueBoolean("false").getDefaultValueBoolean() == false
        new ColumnConfig().setDefaultValueBoolean("FALSE").getDefaultValueBoolean() == false
        new ColumnConfig().setDefaultValueBoolean("0").getDefaultValueBoolean() == false

        new ColumnConfig().setDefaultValueBoolean("bool_val").getDefaultValueComputed().toString() == "bool_val"
        new ColumnConfig().setDefaultValueBoolean("2").getDefaultValueComputed().toString() == "2"
    }

    def setDefaultValueComputed() {
        expect:
        new ColumnConfig().setDefaultValueComputed(null).getDefaultValueComputed() == null
        new ColumnConfig().setDefaultValueComputed(new DatabaseFunction("func")).getDefaultValueComputed().toString() == "func"
    }

    def getDefaultValueObject() {
        expect:
        new ColumnConfig().setDefaultValueBoolean(true).getDefaultValueObject() == true
        new ColumnConfig().setDefaultValueNumeric(5).getDefaultValueObject() == 5
        new ColumnConfig().setDefaultValueDate("1993-02-11T13:22:44.006").getDefaultValueObject().toString() == "1993-02-11 13:22:44.006"
        new ColumnConfig().setDefaultValueComputed(new DatabaseFunction("func")).getDefaultValueObject().toString() == "func"
        new ColumnConfig().setDefaultValue("A value").getDefaultValueObject() == "A value"
        new ColumnConfig().getDefaultValueObject() == null
    }

    def setConstraints() {
        expect:
        new ColumnConfig().setConstraints(null).getConstraints() == null
        new ColumnConfig().setConstraints(new ConstraintsConfig()).getConstraints() != null
    }

    def setAutoIncrement() {
        expect:
        new ColumnConfig().setAutoIncrement(null).isAutoIncrement() == null
        new ColumnConfig().setAutoIncrement(true).isAutoIncrement() == true
        assert !new ColumnConfig().setAutoIncrement(false).isAutoIncrement()
    }

    def setStartWith() {
        expect:
        new ColumnConfig().setStartWith(null).getStartWith() == null
        new ColumnConfig().setStartWith(new BigInteger("125")).getStartWith().toString() == "125"
    }

    def setIncrementBy() {
        expect:
        new ColumnConfig().setIncrementBy(null).getIncrementBy() == null
        new ColumnConfig().setIncrementBy(new BigInteger("131")).getIncrementBy().toString() == "131"
    }

    def hasDefaultValue() {
        expect:
        assert new ColumnConfig().setDefaultValueBoolean(true).hasDefaultValue()
        assert new ColumnConfig().setDefaultValueNumeric(5).hasDefaultValue()
        assert new ColumnConfig().setDefaultValueDate("1993-02-11T13:22:44.006").hasDefaultValue()
        assert new ColumnConfig().setDefaultValueComputed(new DatabaseFunction("func")).hasDefaultValue()
        assert new ColumnConfig().setDefaultValue("A value").hasDefaultValue()
        assert !new ColumnConfig().hasDefaultValue()
    }

    def setRemarks() {
        expect:
        new ColumnConfig().setRemarks(null).getRemarks() == null
        new ColumnConfig().setRemarks("yyy").getRemarks() == "yyy"
    }

    def setValueClob() {
        expect:
        new ColumnConfig().setValueClobFile(null).getValueClobFile() == null
        new ColumnConfig().setValueClobFile("clob_file").getValueClobFile() == "clob_file"
    }

    def setValueBlob() {
        expect:
        new ColumnConfig().setValueBlobFile(null).getValueBlobFile() == null
        new ColumnConfig().setValueBlobFile("blob_file").getValueBlobFile() == "blob_file"
    }

    def getFieldSerializationType() {
        expect:
        new ColumnConfig().getSerializableFieldType("anythiny") == LiquibaseSerializable.SerializationType.NAMED_FIELD
    }

    def getSerializedObjectName() {
        expect:
        new ColumnConfig().getSerializedObjectName() == "column"
    }

    @Unroll("#featureName: #field")
    def "load method sets properties"() {
        when:
        def node = new ParsedNode(null, "column")
        def column = new ColumnConfig()

        def testValue = "value for ${field}"
        if (field in ["defaultValueDate", "valueDate"]) {
            testValue = "2012-03-13 18:52:22.129"
        } else if (field in ["defaultValueBoolean","valueBoolean", "autoIncrement"]) {
            testValue = "true"
        } else if (field in ["startWith", "incrementBy"]) {
            testValue = "838"
        } else if (field in ["valueNumeric", "defaultValueNumeric"]) {
            testValue = "347.22"
        }
        node.addChild(null, field, testValue)
        column.load(node)

        then:
        assert column.getSerializableFieldValue(field).toString() == testValue.toString()

        where:
        field << new ColumnConfig().getSerializableFields().findAll( {!it.equals("constraints")})
    }

    @Unroll("#featureName: #field")
    def "load method sets constraints properties"() {
        when:
        def node = new ParsedNode(null, "column")
        def constraintNode = new ParsedNode(null, "constraints");
        node.addChild(constraintNode)

        def column = new ColumnConfig()

        def testValue = "value for ${field}"
        if (field in ["unique", "deferrable", "nullable", "deleteCascade", "initiallyDeferred", "primaryKey"]) {
            testValue = "true"
        }
        constraintNode.addChild(null, field, testValue)
        column.load(node)

        then:
        assert column.getConstraints().getSerializableFieldValue(field).toString() == testValue.toString()

        where:
        field << new ConstraintsConfig().getSerializableFields()


    }

}
