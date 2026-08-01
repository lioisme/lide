-- =====================================
-- page_calc.lua - Calculator page
-- .alp multi-page example
-- =====================================

local page = {}

function page.add(a, b)
    return a + b
end

function page.sub(a, b)
    return a - b
end

function page.mul(a, b)
    return a * b
end

function page.div(a, b)
    if b == 0 then
        return nil, "division by zero"
    end
    return a / b
end

function page.factorial(n)
    if n <= 1 then return 1 end
    return n * page.factorial(n - 1)
end

return page
