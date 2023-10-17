package sk.tuke.meta.example;

import sk.tuke.meta.persistence.*;
import sk.tuke.meta.persistence.GeneratedPersistenceManager;

import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

//import sk.tuke.meta.example.TableGenerator;

public class Main {
    public static final String DB_PATH = "test.db";

    public static void main(String[] args) throws Exception {
        Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
//        conn.setAutoCommit(true);
//        PersistenceManager manager = new ReflectivePersistenceManager(
//                conn, Person.class, Department.class);


        transactions(conn);

        conn.close();
    }

//    @AtomicPersistenceOperation
    public static void transactions(Connection conn) throws SQLException, ClassNotFoundException, InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {

        PersistenceManager manager = new GeneratedPersistenceManager(
                conn);

        Department development = new Department("Development", "DVLP");
        Department marketing = new Department("Marketing", "MARK");
        Department operations = new Department("Operations", "OPRS");

        Person hrasko = new Person("Janko", "Hrasko", 30);
        hrasko.setDepartment(development);
        Person mrkvicka = new Person("Jozko", "Mrkvicka", 25);
        Person mrkvicka2 = new Person("Janko", "Mrkvicka", 27);
        Person zaurka = new Person("Zaur", "Tregulov", 22);
        zaurka.setDepartment(development);
        mrkvicka2.setDepartment(operations);
        mrkvicka.setDepartment(development);
        Person novak = new Person("Jan", "Novak", 45);
        novak.setDepartment(marketing);

        manager.createTables();

        manager.save(hrasko);
        manager.save(mrkvicka);
        manager.save(zaurka);
        manager.save(novak);
        manager.save(mrkvicka2);
//        manager.save(operations);
//
        System.out.println(manager.get(Person.class,3).get());
        System.out.println(manager.get(Department.class,2).get());
        manager.delete(hrasko);

        List<Person> persons = manager.getAll(Person.class);
        for (Person person : persons) {
            System.out.println(person);
            System.out.println("  " + person.getDepartment());
        }

        List<Department> deps = manager.getAll(Department.class);
        for (Department dep : deps) {
            System.out.println(dep);
        }

        List<Department> depsBy = manager.getBy(Department.class,"code","DVLP");
        for (Department dep : depsBy) {
            System.out.println(dep);
        }

        List<Person> personsBy = manager.getBy(Person.class,"name","Hrasko");
        for (Person person : personsBy) {
            System.out.println(person);
            System.out.println(person.getDepartment());
        }

        manager.delete(development);

    }
}
