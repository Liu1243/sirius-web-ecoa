all: test

test:
	gcc -g -Wall -o test ../src/myXXXDevice_mod.c myXXXDevice_mod_test_container.c myXXXDevice_mod_test.c -I ../inc -I ../inc-gen/ -I ../../../../0-Types/inc-gen/ -DECOA_64BIT_SUPPORT

clean:
	rm -f test
