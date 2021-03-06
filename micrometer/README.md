Sample Requests
---------------
http://localhost:8080/actuator/metrics/http.server.requests
http://localhost:8080/actuator/metrics/http.server.requests?tag=method:GET

prometheus.yml
-------------
# my global config
global:
  scrape_interval:     15s # Set the scrape interval to every 15 seconds. Default is every 1 minute.
  evaluation_interval: 15s # Evaluate rules every 15 seconds. The default is every 1 minute.
  # scrape_timeout is set to the global default (10s).

# Alertmanager configuration
alerting:
  alertmanagers:
  - static_configs:
    - targets:
      # - alertmanager:9093

# Load rules once and periodically evaluate them according to the global 'evaluation_interval'.
rule_files:
  # - "first_rules.yml"
  # - "second_rules.yml"

# A scrape configuration containing exactly one endpoint to scrape:
# Here it's Prometheus itself.
scrape_configs:
  # The job name is added as a label `job=<job_name>` to any timeseries scraped from this config.
  - job_name: 'prometheus'

    # metrics_path defaults to '/metrics'
    # scheme defaults to 'http'.

    static_configs:
    - targets: ['localhost:9090']

  - job_name: 'spring-booot-micrometer'
    metrics_path : '/actuator/prometheus'
    static_configs:
    - targets: ['localhost:8080']

Prometheus Sample Queries
-------------------------
transform_photo_task_seconds_count{region="us-west"}
transform_photo_task_seconds_count{region=~ "us-.*" }
transform_photo_task_seconds_count{ format != "png", region != "us-west" }
