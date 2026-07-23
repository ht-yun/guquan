 package com.guquan.equity.model;
 
import java.util.List;
 import lombok.Builder;
 import lombok.Data;
 
 @Data
 @Builder
 public class CompanyLookupResult {
 
     private String queryId;
 
     private CompanyProfile company;
 
     private List<CompanyProfile> companyCandidates;
 
     private QueryStatus status;
 
     private String confidence;
 
     private String message;

}
