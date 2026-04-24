package com.fintrack.admin;

import com.fintrack.common.entity.AdminSetting;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for system-wide admin configuration. */
public interface AdminSettingRepository extends JpaRepository<AdminSetting, String> {}
