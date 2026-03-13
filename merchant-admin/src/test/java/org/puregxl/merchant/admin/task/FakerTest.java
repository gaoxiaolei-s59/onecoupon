package org.puregxl.merchant.admin.task;

import com.github.javafaker.Faker;
import com.github.javafaker.PhoneNumber;
import org.junit.jupiter.api.Test;

import java.util.Locale;

public class FakerTest {

    @Test
    public void testFaker() {
        Faker faker = new Faker(Locale.CHINA);

        String name = faker.name().fullName();
        System.out.println("电子邮箱" + name);
        PhoneNumber phoneNumber = faker.phoneNumber();

        String mobelPhone = phoneNumber.cellPhone();
        System.out.println("电子邮箱" + phoneNumber);
        String email = faker.internet().emailAddress();
        System.out.println("电子邮箱" + email);
    }

    public void testGit() {

    }
}
