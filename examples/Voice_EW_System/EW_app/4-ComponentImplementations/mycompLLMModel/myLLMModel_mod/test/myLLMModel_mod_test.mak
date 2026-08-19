all: test

test:
	gcc -g -Wall -o test ../src/myLLMModel_mod.c myLLMModel_mod_test_container.c myLLMModel_mod_test.c -I ../inc -I ../inc-gen/ -I ../../../../0-Types/inc-gen/ -DECOA_64BIT_SUPPORT

clean:
	rm -f test
