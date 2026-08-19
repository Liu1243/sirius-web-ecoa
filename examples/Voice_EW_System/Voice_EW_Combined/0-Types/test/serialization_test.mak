all:
	gcc -o serialization_test *.c -Wall -Werror -I ../inc-gen/ -I . -I $(ECOA_C_REPO) -DECOA_64BIT_SUPPORT

clean:
	rm -f test
