package com.crackit.discovery.repository;

import com.crackit.discovery.entity.CuratedCompany;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CuratedCompanyRepository extends JpaRepository<CuratedCompany, String> {

    List<CuratedCompany> findByIsActiveTrue();

    Optional<CuratedCompany> findByNameIgnoreCase(String name);

    Optional<CuratedCompany> findByAtsBoardTokenAndAtsPlatform(String atsBoardToken, String atsPlatform);
}
