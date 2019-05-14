package org.tron.core.db.api.pojo;

import lombok.Data;

import java.util.List;

@Data(staticConstructor = "of")
public class Block {

	private String id;
	private long number;
	private List<String> transactionIds;
}
