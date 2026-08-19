all: helpers

helpers:
	gcc -g -Wall -o helpers_test myVoiceMgmt_mod_helpers.c myVoiceMgmt_mod_test_container.c myVoiceMgmt_mod_helpers_test.c -I ../inc -I ../inc-gen/ -I ../../../../0-Types/inc-gen/ -DECOA_64BIT_SUPPORT

clean:
	rm -f helpers_test
