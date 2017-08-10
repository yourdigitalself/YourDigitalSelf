package com.rutgers.neemi.model;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import java.io.Serializable;

/**
 * Created by suitcase on 7/19/17.
 */
@DatabaseTable(tableName = "PaymentHasCategory")
public class PaymentHasCategory implements Serializable {


    @DatabaseField(generatedId = true)
    int _id;

    // This is a foreign object which just stores the id from the Person object in this table.
    @DatabaseField(foreign = true, columnName = "transaction_id")
    Payment transaction;

    // This is a foreign object which just stores the id from the Post object in this table.
    @DatabaseField(foreign = true, columnName = "category_id")
    PaymentCategory category;

    public PaymentHasCategory() {
        // for ormlite
    }

    public PaymentHasCategory(Payment transaction, PaymentCategory category) {
        this.transaction = transaction;
        this.category = category;
    }

}
