
{{- define "baseName.name" -}}
{{- default .Chart.Name | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "baseName.fullname" -}}
{{- printf "%s" .Release.Name | trunc 63 | trimSuffix "-" -}}
{{- end -}}


{{- define "baseName.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" -}}
{{- end -}}


{{- define "common.env" }}
{{- range $key, $value := .Values.environments }}
- name: "{{ $key }}"
  value: "{{ $value }}"
{{- end }}
{{- end }}