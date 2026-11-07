package main

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

func TestScanConsumesNonceAndKeepsCodePrivate(t *testing.T) {
	s := &PairServer{pending:&Pending{
		ID:strings.Repeat("a",32), Nonce:strings.Repeat("b",64),
		Expires:time.Now().Add(time.Minute),
	}}
	body := strings.NewReader("{\"session_id\":\""+s.pending.ID+"\",\"nonce\":\""+s.pending.Nonce+"\"}")
	req := httptest.NewRequest(http.MethodPost,"/api/v1/pair/scan",body)
	rec := httptest.NewRecorder()
	s.scan(rec,req)
	if rec.Code != http.StatusOK { t.Fatalf("scan status = %d: %s",rec.Code,rec.Body.String()) }
	if !s.pending.Scanned || len(s.pending.Code)!=6 { t.Fatal("scan did not create the second-stage code") }
	if strings.Contains(rec.Body.String(),s.pending.Code) { t.Fatal("computer code must not be returned to the phone during QR validation") }

	req = httptest.NewRequest(http.MethodPost,"/api/v1/pair/scan",strings.NewReader("{\"session_id\":\""+s.pending.ID+"\",\"nonce\":\""+s.pending.Nonce+"\"}"))
	rec = httptest.NewRecorder()
	s.scan(rec,req)
	if rec.Code != http.StatusConflict { t.Fatalf("replayed QR status = %d, want 409",rec.Code) }
}

func TestPairCodeIsSingleUseAndOnlyHashIsPersisted(t *testing.T) {
	dir:=t.TempDir()
	s:=&PairServer{stateFile:filepath.Join(dir,"devices.json"),pending:&Pending{
		ID:strings.Repeat("c",32),Nonce:strings.Repeat("d",64),
		Expires:time.Now().Add(time.Minute),Scanned:true,Code:"731905",
	}}
	payload:=map[string]string{
		"session_id":s.pending.ID,"code":"731905",
		"device_id":"android-device-01","device_name":"Android Test",
	}
	raw,_:=json.Marshal(payload)
	req:=httptest.NewRequest(http.MethodPost,"/api/v1/pair/complete",strings.NewReader(string(raw)))
	rec:=httptest.NewRecorder()
	s.complete(rec,req)
	if rec.Code!=http.StatusOK { t.Fatalf("complete status = %d: %s",rec.Code,rec.Body.String()) }
	var response map[string]any
	if err:=json.Unmarshal(rec.Body.Bytes(),&response);err!=nil { t.Fatal(err) }
	token,ok:=response["access_token"].(string)
	if !ok||len(token)!=64 { t.Fatal("missing random access token") }
	stored,err:=os.ReadFile(s.stateFile)
	if err!=nil { t.Fatal(err) }
	if strings.Contains(string(stored),token) { t.Fatal("raw access token was persisted") }
	if !strings.Contains(string(stored),tokenHash(token)) { t.Fatal("token hash not persisted") }

	check:=httptest.NewRequest(http.MethodGet,"/api/v1/pair/verify",nil)
	check.Header.Set("Authorization","Bearer "+token)
	device,ok:=s.authenticatedDevice(check)
	if !ok||device.ID!="android-device-01" { t.Fatal("issued credential did not authenticate") }

	req=httptest.NewRequest(http.MethodPost,"/api/v1/pair/complete",strings.NewReader(string(raw)))
	rec=httptest.NewRecorder()
	s.complete(rec,req)
	if rec.Code!=http.StatusGone { t.Fatalf("replayed code status = %d, want 410",rec.Code) }
}

func TestPairCodeRateLimit(t *testing.T) {
	s:=&PairServer{pending:&Pending{
		ID:strings.Repeat("e",32),Nonce:strings.Repeat("f",64),
		Expires:time.Now().Add(time.Minute),Scanned:true,Code:"123456",
	}}
	for i:=0;i<maxAttempts;i++ {
		body:="{\"session_id\":\""+s.pending.ID+"\",\"code\":\"000000\",\"device_id\":\"device-123456\",\"device_name\":\"Phone\"}"
		req:=httptest.NewRequest(http.MethodPost,"/api/v1/pair/complete",strings.NewReader(body))
		rec:=httptest.NewRecorder()
		s.complete(rec,req)
		if rec.Code!=http.StatusUnauthorized { t.Fatalf("attempt %d returned %d",i+1,rec.Code) }
	}
	body:="{\"session_id\":\""+s.pending.ID+"\",\"code\":\"123456\",\"device_id\":\"device-123456\",\"device_name\":\"Phone\"}"
	req:=httptest.NewRequest(http.MethodPost,"/api/v1/pair/complete",strings.NewReader(body))
	rec:=httptest.NewRecorder()
	s.complete(rec,req)
	if rec.Code!=http.StatusTooManyRequests { t.Fatalf("after limit returned %d",rec.Code) }
	if s.pending!=nil { t.Fatal("locked session should be invalidated after attempt limit") }
}
