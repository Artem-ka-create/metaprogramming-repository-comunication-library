package sk.tuke.meta.example;


import javax.persistence.*;

@Entity
@Table(name = "pers")
public class Person {
    @Id
    @Column(name = "id")
    @GeneratedValue()
    private long id;

    @Column(name = "surname", nullable = true)
    private String surname;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "age" ,nullable = false)
    private int age;

    @ManyToOne(fetch = FetchType.LAZY,targetEntity = Department.class)
    private IDepartment department;

    public Person(String surname, String name, int age) {
        this.surname = surname;
        this.name = name;
        this.age = age;
    }

    public Person() {
    }

    public void setId(long id) {
        this.id = id;
    }

    public IDepartment getDepartment() {
        return department;
    }

    public void setDepartment(IDepartment department) {
        this.department = department;
    }

    public String getSurname() {
        return surname;
    }

    public void setSurname(String surname) {
        this.surname = surname;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public long getId() {
        return id;
    }


//    @Override
//    public String toString() {
//        return String.format("Person %d: %s %s (%d)", id, surname, name, age);
//    }

    @Override
    public String toString() {
        return "Person{" +
                "id=" + id +
                ", surname='" + surname + '\'' +
                ", name='" + name + '\'' +
                ", age=" + age +
                ", department=" + department +
                '}';
    }

//    @Override
//    public Object getRelationObject() {
//
//        for (Field f:this.getClass().getDeclaredFields()) {
//            if (f.isAnnotationPresent(ManyToOne.class)){
//                f.setAccessible(true);
//
//                try {
//                   return f.get(this);
//                } catch (IllegalAccessException e) {
//                    throw new RuntimeException(e);
//                }
//            }
//        }
//        return null;
//    }
}
