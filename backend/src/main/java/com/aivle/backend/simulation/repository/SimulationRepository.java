package com.aivle.backend.simulation.repository;
import com.aivle.backend.simulation.entity.Simulation;
import org.springframework.data.jpa.repository.JpaRepository;
public interface SimulationRepository extends JpaRepository<Simulation, Long> {}
