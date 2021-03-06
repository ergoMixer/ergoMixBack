/*
 * Ergo Node API
 * API docs for Ergo Node. Models are shared between all Ergo products
 *
 * OpenAPI spec version: 0.1
 * Contact: ergoplatform@protonmail.com
 *
 * NOTE: This class is auto generated by the swagger code generator program.
 * https://github.com/swagger-api/swagger-codegen.git
 * Do not edit the class manually.
 */

package org.ergoplatform.restapi.client;

import java.util.Objects;

import com.google.gson.annotations.SerializedName;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * BlockTransactions
 */

@javax.annotation.Generated(value = "io.swagger.codegen.v3.generators.java.JavaClientCodegen", date = "2019-10-19T14:53:04.559Z[GMT]")
public class BlockTransactions {
  @SerializedName("headerId")
  private String headerId = null;

  @SerializedName("transactions")
  private Transactions transactions = null;

  @SerializedName("size")
  private Integer size = null;

  public BlockTransactions headerId(String headerId) {
    this.headerId = headerId;
    return this;
  }

   /**
   * Get headerId
   * @return headerId
  **/
  @Schema(required = true, description = "")
  public String getHeaderId() {
    return headerId;
  }

  public void setHeaderId(String headerId) {
    this.headerId = headerId;
  }

  public BlockTransactions transactions(Transactions transactions) {
    this.transactions = transactions;
    return this;
  }

   /**
   * Get transactions
   * @return transactions
  **/
  @Schema(required = true, description = "")
  public Transactions getTransactions() {
    return transactions;
  }

  public void setTransactions(Transactions transactions) {
    this.transactions = transactions;
  }

  public BlockTransactions size(Integer size) {
    this.size = size;
    return this;
  }

   /**
   * Size in bytes
   * @return size
  **/
  @Schema(required = true, description = "Size in bytes")
  public Integer getSize() {
    return size;
  }

  public void setSize(Integer size) {
    this.size = size;
  }


  @Override
  public boolean equals(java.lang.Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    BlockTransactions blockTransactions = (BlockTransactions) o;
    return Objects.equals(this.headerId, blockTransactions.headerId) &&
        Objects.equals(this.transactions, blockTransactions.transactions) &&
        Objects.equals(this.size, blockTransactions.size);
  }

  @Override
  public int hashCode() {
    return Objects.hash(headerId, transactions, size);
  }


  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class BlockTransactions {\n");
    
    sb.append("    headerId: ").append(toIndentedString(headerId)).append("\n");
    sb.append("    transactions: ").append(toIndentedString(transactions)).append("\n");
    sb.append("    size: ").append(toIndentedString(size)).append("\n");
    sb.append("}");
    return sb.toString();
  }

  /**
   * Convert the given object to string with each line indented by 4 spaces
   * (except the first line).
   */
  private String toIndentedString(java.lang.Object o) {
    if (o == null) {
      return "null";
    }
    return o.toString().replace("\n", "\n    ");
  }

}
