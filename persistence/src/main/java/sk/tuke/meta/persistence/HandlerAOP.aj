package sk.tuke.meta.persistence;


import java.sql.Connection;
import java.sql.SQLException;

import org.aspectj.lang.ProceedingJoinPoint;
import sk.tuke.meta.persistence.AtomicPersistenceOperation;

public aspect HandlerAOP {


    Connection connection;
    boolean commitStatus = true;

    pointcut daoPersistenceManagerCreation(Connection connection):
            execution(public DAOPersistenceManager.new(Connection)) && args(connection);

    pointcut transactionHandler():
            execution(@AtomicPersistenceOperation * * (..)) ;

    pointcut setDBSettings():
            execution(public void DAOPersistenceManager.setDbConfiguration()) ;

    after():setDBSettings(){
        System.out.println("set COMMIT TRY");
        if (commitStatus){

            try {
                this.connection.setAutoCommit(false);
                System.out.println("set COMMIT DONE");
                commitStatus = false;

            } catch (SQLException e) {
                System.out.println("set COMMIT FALSE");

                throw new RuntimeException(e);
            }
        }

    }

    before(Connection connection): daoPersistenceManagerCreation(connection){
        this.connection = connection;
    }

    Object around():transactionHandler(){
        Object result = null;
        try{
            result = proceed();

            connection.commit();
        }catch (Throwable e){
                System.out.println("ROLL");
            try {
                connection.rollback();
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
        }
        return result;
    }

}
