package liquibase.database.core.mssql;


import liquibase.database.AbstractJdbcDatabase;
import liquibase.database.ConnectionSupplier;
import liquibase.database.Database;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

public class MSSQLConnectionSupplier extends ConnectionSupplier {
    @Override
    public String getDatabaseShortName() {
        return "mssql";
    }

    public String getInstanceName() {
        return "MSSQLSERVER";
    }

    @Override
    public String getAdminUsername() {
        return "sa";
    }

    @Override
    public String getJdbcUrl() {
        return "jdbc:sqlserver://"+ getIpAddress() +":1433;databaseName="+getPrimaryCatalog();
    }

    @Override
    public String getOs() {
        return OS_WINDOWS;
    }

    @Override
    public String getPrimaryCatalog() {
        return super.getPrimaryCatalog().toLowerCase();
    }

    @Override
    public String getPrimarySchema() {
        return super.getPrimarySchema().toLowerCase();
    }

    @Override
    public String getAlternateCatalog() {
        return super.getAlternateCatalog().toLowerCase();
    }

    @Override
    public String getAlternateSchema() {
        return super.getAlternateSchema().toLowerCase();
    }

    @Override
    public Database getDatabase() {
        Database database = super.getDatabase();
        ((AbstractJdbcDatabase) database).setCaseSensitive(false);
        return database;
    }

    @Override
    public String getConfigurationName() {
        return "caseInsensitive";
    }

    //    @Override
//    public ConfigTemplate getPuppetTemplate(Map<String, Object> context) {
//        return new ConfigTemplate("liquibase/sdk/vagrant/supplier/mssql/mssql.puppet.vm", context);
//    }

    @Override
    public String getDescription() {
        return super.getDescription() +
                "Instance name: "+getInstanceName()+"\n"+
                "\n"+
                "REQUIRES: You must manually download the sql server express installation files into LIQUIBASE_HOME/sdk/vagrant/install-files/mssql/SQLEXPR_x64_ENU.exe\n"+
                "      You can download the install files from http://www.microsoft.com/en-us/sqlserver/get-sql-server/try-it.aspx#tab2\n"+
                "\n"+
                "NOTE: If Exec[mssql install] fails, you may need to remote desktop to the vagrant box and run the failed command locally. After running, re-run liquibase-sdk vagrant [BOX_NAME] provision. Watch the process manager for SQLEXPR_x64_ENU.exe to exit. You may want to change the '/q' flag to '/qs' for more feedback.\n"+
                "\n";
    }

    @Override
    public Set<ConfigTemplate> generateConfigFiles(Map<String, Object> context) throws IOException {
        Set<ConfigTemplate> configTemplates = super.generateConfigFiles(context);
        configTemplates.add(new ConfigTemplate("liquibase/sdk/vagrant/supplier/mssql/mssql.init.sql.vm", context));

        return configTemplates;
    }


}