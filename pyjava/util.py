def join_natural(iterable, separator=', ', word='and', oxford_comma=True, add_spaces=True):
    if add_spaces:
        if len(word) != 0 and not word[-1].isspace():
            word += ' '
        if len(separator) != 0 and len(word) != 0 and not separator[-1].isspace() and not word[0].isspace():
            word = ' ' + word

    last2 = None
    set_last2 = False
    last1 = None
    set_last1 = False

    result = ""
    for i, item in enumerate(iterable):
        if set_last2:
            if i == 2:
                result += str(last2)
            else:
                result += separator + str(last2)
        last2 = last1
        set_last2 = set_last1
        last1 = item
        set_last1 = True

    if set_last2:
        if result:
            if oxford_comma:
                result += separator + str(last2) + separator + word + str(last1)
            else:
                if add_spaces and not word[0].isspace():
                    word = ' ' + word

                result += separator + str(last2) + word + str(last1)
                
        else:
            if add_spaces and not word[0].isspace():
                word = ' ' + word

            result = str(last2) + word + str(last1)

    elif set_last1:
        result = str(last1)

    return result


class LookAheadListIterator(object):
    def __init__(self, iterable):
        self.list = list(iterable)

        self.marker = 0
        self.saved_markers = []

        self.default = None
        self.value = None

    def __iter__(self):
        return self

    def set_default(self, value):
        self.default = value

    def next(self):
        return self.__next__()

    def previous(self):
        try:
            self.value = self.list[self.marker-1]
            self.marker -= 1
        except IndexError:
            pass
        return self.value

    def __next__(self):
        try:
            self.value = self.list[self.marker]
            self.marker += 1
        except IndexError:
            raise StopIteration()

        return self.value

    def look(self, i=0):
        """ Look ahead of the iterable by some number of values with advancing
        past them.
        If the requested look ahead is past the end of the iterable then None is
        returned.
        """

        try:
            self.value = self.list[self.marker + i]
        except IndexError:
            return self.default

        return self.value

    def last(self):
        return self.value

    def __enter__(self):
        self.push_marker()
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        # Reset the iterator if there was an error
        if exc_type or exc_val or exc_tb:
            self.pop_marker(True)
        else:
            self.pop_marker(False)

    def push_marker(self):
        """ Push a marker on to the marker stack """
        # print('push marker, stack =', self.saved_markers)
        self.saved_markers.append(self.marker)

    def pop_marker(self, reset):
        """ Pop a marker off of the marker stack. If reset is True then the
        iterator will be returned to the state it was in before the
        corresponding call to push_marker().
        """

        saved = self.saved_markers.pop()
        if reset:
            # print(f'reset {saved}, stack =', self.saved_markers)
            self.marker = saved
        # elif self.saved_markers:
        #     print(f'pop marker {saved}, no reset, stack =', self.saved_markers)
            # self.saved_markers[-1] = saved
