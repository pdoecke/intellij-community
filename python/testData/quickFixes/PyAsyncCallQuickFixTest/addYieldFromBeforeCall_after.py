import asyncio


@asyncio.coroutine
def bar():
    yield from asyncio.sleep(2)
    return "hey"


@asyncio.coroutine
def foo():
    yield from bar()
    return True
