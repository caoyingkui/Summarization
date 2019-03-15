package cn.edu.pku.sei.structureAlignment.database;

import mySql.SqlConnector;

import java.util.ResourceBundle;

/**
 * Created by oliver on 2018/2/8.
 * ApiDB is used for connecting the api database.
 */
public class ApiDB {
    public static SqlConnector conn;
    public static String tableName;
    static{
        ResourceBundle bundle = ResourceBundle.getBundle("database");
        String url = bundle.getString("luceneAPI_url");
        String user = bundle.getString("db_user");
        String pwd = bundle.getString("db_pwd");
        String driver = bundle.getString("db_driver");
        tableName = bundle.getString("luceneAPI_table");
        conn = new SqlConnector(url , user , pwd , driver);
        conn.start();
    }
}
